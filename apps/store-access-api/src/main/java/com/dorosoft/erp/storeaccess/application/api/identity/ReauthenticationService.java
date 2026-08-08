package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.ReauthenticationSessionManager;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password reauthentication for important management actions (ADR-02-003/011): a 15-minute
 * {@code authenticatedAt} window separate from login, Session-scoped failure counting (never
 * {@code employee_account}'s login-lockout state), and Session-ID rotation on success.
 */
@Service
public class ReauthenticationService {

    private static final Logger log = LoggerFactory.getLogger(ReauthenticationService.class);
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final String REASON_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    private static final String REASON_TOO_MANY_FAILURES = "TOO_MANY_FAILURES";

    private final EmployeeAccountRepository employeeAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReauthenticationSessionManager sessionManager;
    private final SecurityHistoryRecorder securityHistoryRecorder;
    private final Clock clock;

    public ReauthenticationService(
            EmployeeAccountRepository employeeAccountRepository,
            PasswordEncoder passwordEncoder,
            ReauthenticationSessionManager sessionManager,
            SecurityHistoryRecorder securityHistoryRecorder,
            Clock clock) {
        this.employeeAccountRepository = employeeAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionManager = sessionManager;
        this.securityHistoryRecorder = securityHistoryRecorder;
        this.clock = clock;
    }

    public void reauthenticate(
            ActorContext actor, String passwordRaw, HttpServletRequest request, HttpServletResponse response) {
        EmployeeAccount account = employeeAccountRepository.findById(actor.employeeId())
                .filter(candidate -> candidate.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new ProblemAwareException(
                        EmployeeManagementProblemCode.EMPLOYEE_NOT_FOUND, "직원을 찾을 수 없습니다."));

        String normalizedPassword = PasswordPolicyValidator.normalize(passwordRaw);
        if (passwordEncoder.matches(normalizedPassword, account.passwordHash())) {
            sessionManager.recordSuccessAndRotate(request, response, clock.instant());
            return;
        }

        recordHistorySafely(() -> securityHistoryRecorder.recordReauthenticationFailed(
                actor.tenantId(), actor.storeId(), actor.employeeId(), REASON_INVALID_CREDENTIALS));

        int failures = sessionManager.recordFailureAndGetCount(request);
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            sessionManager.invalidateCurrentSession(request, response);
            recordHistorySafely(() -> securityHistoryRecorder.recordSessionInvalidated(
                    actor.tenantId(), actor.storeId(), actor.employeeId(), REASON_TOO_MANY_FAILURES));
            throw new ProblemAwareException(AuthProblemCode.SESSION_INVALIDATED, "Session이 무효화되었습니다.");
        }
        throw new ProblemAwareException(AuthProblemCode.AUTHENTICATION_FAILED, "비밀번호가 올바르지 않습니다.");
    }

    /** ADR-02-015: a history-write failure must never change the reauthentication outcome itself. */
    private void recordHistorySafely(Runnable recording) {
        try {
            recording.run();
        } catch (RuntimeException e) {
            log.warn("Failed to record reauthentication security history", e);
        }
    }
}
