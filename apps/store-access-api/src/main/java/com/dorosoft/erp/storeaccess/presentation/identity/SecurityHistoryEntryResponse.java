package com.dorosoft.erp.storeaccess.presentation.identity;

import java.time.Instant;
import java.util.UUID;

public record SecurityHistoryEntryResponse(
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
}
