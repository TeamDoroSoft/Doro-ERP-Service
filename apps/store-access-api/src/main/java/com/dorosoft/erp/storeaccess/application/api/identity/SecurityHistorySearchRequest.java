package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Caller-supplied local security history search filters (ADR-02-015). {@code cursorOccurredAt}/
 * {@code cursorId} must both be present or both absent. {@code size} may be {@code null} to use the default
 * page size (50).
 */
public record SecurityHistorySearchRequest(
        Instant from,
        Instant to,
        SecurityHistoryEventType eventType,
        String targetType,
        UUID targetId,
        SecurityHistoryResult result,
        Instant cursorOccurredAt,
        UUID cursorId,
        Integer size) {
}
