package com.dorosoft.erp.audit.application.port;

import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.application.model.AuditRecord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuditAppendPort {
    void append(AuditRecord record);

    Optional<ExistingAuditRecord> findByOperation(AuditDomain domain, UUID operationId, int eventSequence);

    record ExistingAuditRecord(UUID auditId, byte[] payloadHmac, Instant occurredAt) {
    }
}
