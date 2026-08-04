package com.dorosoft.erp.audit.application.api;

import com.dorosoft.erp.audit.domain.AuditTargetType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditRecordProjection(
        UUID auditId,
        String domain,
        String action,
        String actorType,
        UUID actorId,
        String actorRoleSnapshot,
        String actorDisplayNameSnapshot,
        String reasonCode,
        String reason,
        AuditTargetType primaryTargetType,
        UUID primaryTargetId,
        List<String> changedFields,
        Instant occurredAt,
        String requestId,
        Map<String, Object> beforeValue,
        Map<String, Object> afterValue,
        Integer valueSchemaVersion
) {
}
