package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSessionRevoker;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delegates to {@link SessionInvalidator} (ADR-02-002/004: retried deletion, never rolls back the caller's
 * already-committed PostgreSQL change). A final failure is logged here — with only the safe fields
 * {@link SessionInvalidator} itself already restricts itself to — and swallowed, matching "삭제 실패는 제한된
 * 재시도, Metric·구조화 경고 로그와 Session TTL로 처리한다".
 */
@Component
class SessionInvalidatorEmployeeSessionRevoker implements EmployeeSessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(SessionInvalidatorEmployeeSessionRevoker.class);

    private final SessionInvalidator sessionInvalidator;

    SessionInvalidatorEmployeeSessionRevoker(SessionInvalidator sessionInvalidator) {
        this.sessionInvalidator = sessionInvalidator;
    }

    @Override
    public void revokeAllSessions(UUID tenantId, UUID employeeId) {
        SessionInvalidationResult result =
                sessionInvalidator.invalidateAll(new SessionPrincipalIndexKey(tenantId, employeeId));
        if (!result.succeeded()) {
            log.warn("Employee Session revocation did not succeed after {} attempts; employeeId={}",
                    result.attempts(), employeeId);
        }
    }
}
