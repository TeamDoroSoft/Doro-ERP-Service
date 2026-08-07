package com.dorosoft.erp.audit.domain.record;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditRecord(
        AuditSource source,
        int eventVersion,
        UUID tenantId,
        UUID storeId,
        AuditActor actor,
        String action,
        AuditTarget target,
        String result,
        String reasonCode,
        Map<String, Object> metadata,
        String traceId,
        Instant occurredAt,
        Instant receivedAt,
        Instant expiresAt) {

    public AuditRecord {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
