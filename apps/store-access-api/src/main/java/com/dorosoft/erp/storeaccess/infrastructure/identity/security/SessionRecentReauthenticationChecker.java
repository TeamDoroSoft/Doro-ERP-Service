package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.api.identity.AuthProblemCode;
import com.dorosoft.erp.storeaccess.application.port.identity.RecentReauthenticationChecker;
import com.dorosoft.erp.storeaccess.infrastructure.identity.session.EmployeeSessionAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads {@code authenticatedAt} off the current request's already-resolved Session (ADR-02-003/011). Uses
 * {@link RequestContextHolder} to reach the current {@link HttpServletRequest} without threading it through
 * every affected Application Service method signature.
 */
@Component
class SessionRecentReauthenticationChecker implements RecentReauthenticationChecker {

    private static final Duration REAUTHENTICATION_VALIDITY = Duration.ofMinutes(15);

    private final Clock clock;

    SessionRecentReauthenticationChecker(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void requireRecentReauthentication() {
        HttpServletRequest request = currentRequest();
        Object resolved = request.getAttribute(EmployeeSessionAuthenticationFilter.RESOLVED_SESSION_REQUEST_ATTRIBUTE);
        Instant authenticatedAt = resolved instanceof Session session
                ? session.getAttribute(EmployeeSessionAttributes.AUTHENTICATED_AT)
                : null;
        if (authenticatedAt == null
                || Duration.between(authenticatedAt, clock.instant()).compareTo(REAUTHENTICATION_VALIDITY) > 0) {
            throw new ProblemAwareException(
                    AuthProblemCode.REAUTHENTICATION_REQUIRED, "중요 작업을 진행하려면 재인증이 필요합니다.");
        }
    }

    private HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
