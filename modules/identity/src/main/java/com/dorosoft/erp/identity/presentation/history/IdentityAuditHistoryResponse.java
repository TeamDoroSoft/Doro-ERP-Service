package com.dorosoft.erp.identity.presentation.history;

import com.dorosoft.erp.identity.application.history.IdentityAuditEventItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IdentityAuditHistoryResponse(
        List<Item> items,
        String nextCursor
) {
    public IdentityAuditHistoryResponse {
        items = List.copyOf(items);
    }

    public record Item(
            UUID eventId,
            String sourceType,
            String eventType,
            String outcome,
            Actor actor,
            UUID targetAccountId,
            String failureClass,
            String reason,
            List<String> changedFields,
            Instant occurredAt,
            String requestId
    ) {
        static Item from(IdentityAuditEventItem item) {
            return new Item(
                    item.eventId(), item.sourceType().name(), item.eventType(), item.outcome(),
                    item.actor() == null ? null : new Actor(
                            item.actor().accountId(), item.actor().roleCode(), item.actor().displayNameSnapshot()),
                    item.targetAccountId(), item.failureClass(), item.reason(), item.changedFields(),
                    item.occurredAt(), item.requestId());
        }
    }

    public record Actor(UUID accountId, String roleCode, String displayNameSnapshot) {
    }
}
