package com.dorosoft.erp.storeaccess.domain.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single local security history record (ADR-02-015). Only allowlisted, non-sensitive fields are kept;
 * no free-form payload or request/response body is stored.
 */
public record EmployeeSecurityHistory(
        UUID id,
        UUID tenantId,
        UUID storeId,
        SecurityHistoryEventType eventType,
        UUID actorEmployeeId,
        String targetType,
        UUID targetId,
        SecurityHistoryResult result,
        String reasonCode,
        String previousValue,
        String newValue,
        Instant occurredAt,
        Instant expiresAt) {

    private static final Duration RETENTION = Duration.ofDays(90);

    public EmployeeSecurityHistory {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static EmployeeSecurityHistory occur(
            UUID id,
            UUID tenantId,
            UUID storeId,
            SecurityHistoryEventType eventType,
            UUID actorEmployeeId,
            String targetType,
            UUID targetId,
            SecurityHistoryResult result,
            String reasonCode,
            String previousValue,
            String newValue,
            Instant occurredAt) {
        return new EmployeeSecurityHistory(
                id, tenantId, storeId, eventType, actorEmployeeId, targetType, targetId, result, reasonCode,
                previousValue, newValue, occurredAt, occurredAt.plus(RETENTION));
    }
}
