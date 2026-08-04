package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.identity.application.port.IdentitySecurityEventRepository;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventOutcome;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginRateLimitSecurityEventRecorder {
    private final IdentitySecurityEventRepository securityEvents;

    public LoginRateLimitSecurityEventRecorder(IdentitySecurityEventRepository securityEvents) {
        this.securityEvents = securityEvents;
    }

    /** Commits the deterministic event before Redis is told that this window was recorded. */
    @Transactional
    public void record(UUID limitedWindowId, String requestId, Instant occurredAt) {
        securityEvents.appendIfAbsent(SecurityEvents.event(
                limitedWindowId,
                null,
                SecurityEventType.LOGIN_RATE_LIMITED,
                SecurityEventOutcome.DENIED,
                null,
                requestId,
                occurredAt
        ));
    }
}
