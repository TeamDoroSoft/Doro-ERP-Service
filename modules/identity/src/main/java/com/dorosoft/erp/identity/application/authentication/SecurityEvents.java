package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.identity.domain.securityevent.IdentitySecurityEvent;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventFailureClass;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventOutcome;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventType;
import java.time.Instant;
import java.util.UUID;

final class SecurityEvents {
    private SecurityEvents() {
    }

    static IdentitySecurityEvent event(
            UUID eventId,
            UUID accountId,
            SecurityEventType type,
            SecurityEventOutcome outcome,
            SecurityEventFailureClass failureClass,
            String requestId,
            Instant occurredAt
    ) {
        return new IdentitySecurityEvent(
                eventId, accountId, type, outcome, failureClass, requestId, occurredAt);
    }

    static IdentitySecurityEvent event(
            UUID accountId,
            SecurityEventType type,
            SecurityEventOutcome outcome,
            SecurityEventFailureClass failureClass,
            String requestId,
            Instant occurredAt
    ) {
        return event(UUID.randomUUID(), accountId, type, outcome, failureClass, requestId, occurredAt);
    }
}
