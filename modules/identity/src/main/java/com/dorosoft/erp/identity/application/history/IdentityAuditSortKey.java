package com.dorosoft.erp.identity.application.history;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

public record IdentityAuditSortKey(
        Instant occurredAt,
        IdentityAuditSourceType sourceType,
        UUID eventId
) {
    public static final Comparator<IdentityAuditSortKey> ORDER = Comparator
            .comparing(IdentityAuditSortKey::occurredAt, Comparator.reverseOrder())
            .thenComparingInt(key -> key.sourceType().sourceOrder())
            .thenComparing(key -> key.eventId().toString(), Comparator.reverseOrder());

    public IdentityAuditSortKey {
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(sourceType);
        Objects.requireNonNull(eventId);
    }

    public boolean isAfter(IdentityAuditSortKey cursorBoundary) {
        return cursorBoundary == null || ORDER.compare(this, cursorBoundary) > 0;
    }
}
