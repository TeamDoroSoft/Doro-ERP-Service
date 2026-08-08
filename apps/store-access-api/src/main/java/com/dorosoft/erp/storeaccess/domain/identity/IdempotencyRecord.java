package com.dorosoft.erp.storeaccess.domain.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotency claim scoped to {@code tenantId + actorEmployeeId + operation + keyDigest} (ADR-02-014).
 * {@code keyDigest} and {@code requestHmac} are derived values; the raw {@code Idempotency-Key} and request
 * body are never stored.
 */
public record IdempotencyRecord(
        UUID id,
        UUID tenantId,
        UUID actorEmployeeId,
        String operation,
        String keyDigest,
        String requestHmac,
        IdempotencyRecordStatus status,
        String resultPayload,
        Instant createdAt,
        Instant completedAt,
        Instant expiresAt) {

    public IdempotencyRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(actorEmployeeId, "actorEmployeeId must not be null");
        requireNonBlank(operation, "operation");
        requireNonBlank(keyDigest, "keyDigest");
        requireNonBlank(requestHmac, "requestHmac");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (status == IdempotencyRecordStatus.COMPLETED && completedAt == null) {
            throw new IllegalArgumentException("completedAt is required once the record is completed");
        }
    }

    public static IdempotencyRecord claim(
            UUID id,
            UUID tenantId,
            UUID actorEmployeeId,
            String operation,
            String keyDigest,
            String requestHmac,
            Instant now,
            Instant expiresAt) {
        return new IdempotencyRecord(
                id, tenantId, actorEmployeeId, operation, keyDigest, requestHmac,
                IdempotencyRecordStatus.IN_PROGRESS, null, now, null, expiresAt);
    }

    public IdempotencyRecord complete(String resultPayload, Instant completedAt) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        return new IdempotencyRecord(
                id, tenantId, actorEmployeeId, operation, keyDigest, requestHmac,
                IdempotencyRecordStatus.COMPLETED, resultPayload, createdAt, completedAt, expiresAt);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
