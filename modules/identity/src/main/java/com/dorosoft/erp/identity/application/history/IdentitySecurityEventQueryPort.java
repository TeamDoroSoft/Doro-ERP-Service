package com.dorosoft.erp.identity.application.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IdentitySecurityEventQueryPort {
    List<SecurityEventView> query(
            String eventType,
            UUID targetAccountId,
            Instant from,
            Instant to,
            IdentityAuditSortKey cursorBoundary,
            int limit
    );

    record SecurityEventView(
            UUID eventId,
            String eventType,
            String outcome,
            UUID targetAccountId,
            String failureClass,
            Instant occurredAt,
            String requestId
    ) {
        public IdentityAuditEventItem toItem() {
            return new IdentityAuditEventItem(
                    eventId,
                    IdentityAuditSourceType.IDENTITY_SECURITY_EVENT,
                    eventType,
                    outcome,
                    null,
                    targetAccountId,
                    failureClass,
                    null,
                    List.of(),
                    occurredAt,
                    requestId
            );
        }
    }
}
