package com.dorosoft.erp.platform.messaging.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AuditEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String sourceService,
        UUID tenantId,
        UUID storeId,
        AuditActor actor,
        String action,
        AuditTarget target,
        String result,
        String reasonCode,
        Map<String, Object> metadata,
        String traceId,
        Instant occurredAt) {

    public AuditEventEnvelope {
        if (metadata != null) {
            metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
    }
}
