package com.dorosoft.erp.identity.application.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IdentityAuditEventItem(
        UUID eventId,
        IdentityAuditSourceType sourceType,
        String eventType,
        String outcome,
        IdentityAuditActor actor,
        UUID targetAccountId,
        String failureClass,
        String reason,
        List<String> changedFields,
        Instant occurredAt,
        String requestId
) {
    public IdentityAuditEventItem {
        changedFields = List.copyOf(changedFields);
    }

    public IdentityAuditSortKey sortKey() {
        return new IdentityAuditSortKey(occurredAt, sourceType, eventId);
    }
}
