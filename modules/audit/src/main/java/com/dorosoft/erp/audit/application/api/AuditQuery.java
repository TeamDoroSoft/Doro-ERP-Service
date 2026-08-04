package com.dorosoft.erp.audit.application.api;

import java.util.Optional;
import java.util.UUID;

public interface AuditQuery {

    AuditQueryResult query(String tenantId, AuditQueryFilter filter);

    Optional<AuditRecordProjection> findById(String tenantId, UUID auditId);

    default AuditQueryResult findIdentityDomain(String tenantId, int limit) {
        return query(tenantId, new AuditQueryFilter(
                com.dorosoft.erp.audit.domain.AuditDomain.IDENTITY,
                null,
                null,
                null,
                null,
                null,
                null,
                limit,
                null
        ));
    }
}
