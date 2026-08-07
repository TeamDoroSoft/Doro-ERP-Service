package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import java.time.Instant;
import java.util.UUID;

/**
 * The Controller-safe view of an {@link EmployeeSecurityHistory} record: Event type/Result as plain
 * {@code String}s so the presentation layer — which may not depend on {@code domain} — never references
 * those enum types.
 */
public record SecurityHistoryEntry(
        UUID id,
        String eventType,
        UUID actorEmployeeId,
        String targetType,
        UUID targetId,
        String result,
        String reasonCode,
        String previousValue,
        String newValue,
        Instant occurredAt) {

    static SecurityHistoryEntry from(EmployeeSecurityHistory history) {
        return new SecurityHistoryEntry(
                history.id(), history.eventType().name(), history.actorEmployeeId(), history.targetType(),
                history.targetId(), history.result().name(), history.reasonCode(), history.previousValue(),
                history.newValue(), history.occurredAt());
    }
}
