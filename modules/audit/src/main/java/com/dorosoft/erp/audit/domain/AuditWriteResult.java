package com.dorosoft.erp.audit.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditWriteResult(
        AuditWriteStatus status,
        UUID auditId,
        Instant occurredAt
) {
}
