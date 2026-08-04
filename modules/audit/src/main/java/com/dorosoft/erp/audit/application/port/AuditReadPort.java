package com.dorosoft.erp.audit.application.port;

import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.application.api.AuditRecordProjection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditReadPort {
    List<AuditRecordProjection> query(AuditQueryFilter filter, Instant beforeOccurredAt, UUID beforeAuditId,
                                      int limit, Instant now);

    Optional<AuditRecordProjection> findById(UUID auditId, Instant now);
}
