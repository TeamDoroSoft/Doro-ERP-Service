package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.ActiveTenantContext;
import com.dorosoft.erp.storeaccess.application.port.identity.DummyCredentialProvider;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.LoginRateLimiter;
import com.dorosoft.erp.storeaccess.application.port.identity.TenantLookupPort;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee login (ADR-02-002/003/006/007/008/009): Rate Limiting, {@code tenantCode} resolution, stepped
 * account lockout, a Dummy-Hash verification path for nonexistent accounts, and NFC password normalization.
 *
 * <p>{@link TenantLookupPort} has no production Adapter yet — feature 01 owns {@code tenantCode}
 * Canonicalization and Tenant/Store lookup and has not been implemented in this codebase. Resolved lazily
 * through an {@link ObjectProvider} (the same pattern as {@code SessionInvalidator}) rather than a direct
 * constructor dependency, so this Service — and every other test's full Spring context, which would
 * otherwise fail to start without a bean for this Port — can start regardless of whether feature 01's
 * Adapter is present; only an actual login attempt fails, with
 * {@link AuthProblemCode#SESSION_VALIDATION_UNAVAILABLE}, until it is (CLAUDE_기능02_구현_가이드.md §6).
 */
@Service
public class EmployeeLoginService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeLoginService.class);
    private static final Duration ABSOLUTE_SESSION_DURATION = Duration.ofHours(12);
    private static final String REASON_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    private static final String REASON_LOCKOUT_ESCALATED = "LOCKOUT_ESCALATED";

    private final EmployeeAccountRepository employeeAccountRepository;
    private final ObjectProvider<TenantLookupPort> tenantLookupPortProvider;
    private final LoginRateLimiter loginRateLimiter;
    private final DummyCredentialProvider dummyCredentialProvider;
    private final PasswordEncoder passwordEncoder;
    private final SecurityHistoryRecorder securityHistoryRecorder;
    private final Clock clock;

    public EmployeeLoginService(
            EmployeeAccountRepository employeeAccountRepository,
            ObjectProvider<TenantLookupPort> tenantLookupPortProvider,
            LoginRateLimiter loginRateLimiter,
            DummyCredentialProvider dummyCredentialProvider,
            PasswordEncoder passwordEncoder,
            SecurityHistoryRecorder securityHistoryRecorder,
            Clock clock) {
        this.employeeAccountRepository = employeeAccountRepository;
        this.tenantLookupPortProvider = tenantLookupPortProvider;
        this.loginRateLimiter = loginRateLimiter;
        this.dummyCredentialProvider = dummyCredentialProvider;
        this.passwordEncoder = passwordEncoder;
        this.securityHistoryRecorder = securityHistoryRecorder;
        this.clock = clock;
    }

    /**
     * {@code noRollbackFor} is required: a failed attempt still commits the account's incremented
     * failure/lockout state before this method throws {@link AuthProblemCode#AUTHENTICATION_FAILED} to the
     * caller — Spring's default behavior would otherwise roll back that very state change because a
     * {@code RuntimeException} escaped a {@code @Transactional} method.
     */
    @Transactional(noRollbackFor = ProblemAwareException.class)
    public LoginResult login(LoginCommand command) {
        LoginId loginId;
        try {
            loginId = LoginId.normalize(command.loginId());
        } catch (IllegalArgumentException e) {
            throw authenticationFailedAfterDummyCheck(command.password());
        }

        if (!loginRateLimiter.tryConsumeAccountScope(command.tenantCode(), loginId)
                || !loginRateLimiter.tryConsumeClientIpScope(command.clientIp())) {
            throw new ProblemAwareException(AuthProblemCode.AUTH_RATE_LIMITED, "요청이 너무 많습니다.");
        }

        TenantLookupPort tenantLookupPort = tenantLookupPortProvider.getIfAvailable();
        if (tenantLookupPort == null) {
            throw new ProblemAwareException(
                    AuthProblemCode.SESSION_VALIDATION_UNAVAILABLE, "로그인을 처리할 수 없습니다.");
        }
        Optional<ActiveTenantContext> tenant = tenantLookupPort.resolveActiveTenantByCode(command.tenantCode());
        if (tenant.isEmpty()) {
            throw authenticationFailedAfterDummyCheck(command.password());
        }

        Optional<EmployeeAccount> maybeAccount =
                employeeAccountRepository.findByTenantIdAndLoginId(tenant.get().tenantId(), loginId);
        if (maybeAccount.isEmpty()) {
            throw authenticationFailedAfterDummyCheck(command.password());
        }

        EmployeeAccount account = maybeAccount.get();
        Instant now = clock.instant();
        if (account.isLocked(now)) {
            throw authenticationFailed();
        }

        String normalizedPassword = PasswordPolicyValidator.normalize(command.password());
        if (!passwordEncoder.matches(normalizedPassword, account.passwordHash())) {
            recordFailedAttempt(tenant.get(), account, now);
            throw authenticationFailed();
        }
        if (account.status() != EmployeeStatus.ACTIVE) {
            recordHistorySafely(() -> securityHistoryRecorder.recordLoginFailed(
                    tenant.get().tenantId(), tenant.get().storeId(), account.id(), REASON_INVALID_CREDENTIALS));
            throw authenticationFailed();
        }

        EmployeeAccount updated = account.recordSuccessfulLogin(now);
        employeeAccountRepository.save(updated);
        return new LoginResult(
                tenant.get().tenantId(), tenant.get().storeId(), account.id(), account.role(), loginId.value(),
                updated.passwordChangeRequired(), now, now.plus(ABSOLUTE_SESSION_DURATION));
    }

    private void recordFailedAttempt(ActiveTenantContext tenant, EmployeeAccount account, Instant now) {
        EmployeeAccount updated = account.recordFailedLogin(now);
        employeeAccountRepository.save(updated);
        boolean justLocked = updated.isLocked(now);
        recordHistorySafely(() -> {
            securityHistoryRecorder.recordLoginFailed(
                    tenant.tenantId(), tenant.storeId(), account.id(), REASON_INVALID_CREDENTIALS);
            if (justLocked) {
                securityHistoryRecorder.recordAccountLocked(
                        tenant.tenantId(), tenant.storeId(), account.id(), REASON_LOCKOUT_ESCALATED);
            }
        });
    }

    /**
     * ADR-02-015: a history-write failure must never turn an already-decided login failure into a
     * {@code 500}, so this always swallows (log-only) whatever {@link SecurityHistoryRecorder} throws.
     */
    private void recordHistorySafely(Runnable recording) {
        try {
            recording.run();
        } catch (RuntimeException e) {
            log.warn("Failed to record login-failure security history", e);
        }
    }

    private ProblemAwareException authenticationFailedAfterDummyCheck(String rawPassword) {
        String normalized = PasswordPolicyValidator.normalize(rawPassword);
        passwordEncoder.matches(normalized, dummyCredentialProvider.dummyPasswordHash());
        return authenticationFailed();
    }

    private ProblemAwareException authenticationFailed() {
        return new ProblemAwareException(
                AuthProblemCode.AUTHENTICATION_FAILED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
