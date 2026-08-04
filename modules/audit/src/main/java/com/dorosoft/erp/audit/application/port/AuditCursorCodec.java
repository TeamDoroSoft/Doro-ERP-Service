package com.dorosoft.erp.audit.application.port;

import com.dorosoft.erp.audit.application.api.AuditQueryFilter;

import java.time.Instant;
import java.util.UUID;

public interface AuditCursorCodec {
    String encode(String tenantId, AuditQueryFilter filter, Instant occurredAt, UUID auditId);

    Position decode(String tenantId, AuditQueryFilter filter, String cursor);

    record Position(Instant occurredAt, UUID auditId) {
    }
}
