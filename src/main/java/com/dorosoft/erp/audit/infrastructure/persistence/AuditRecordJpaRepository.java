package com.dorosoft.erp.audit.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordJpaRepository extends JpaRepository<AuditRecordEntity, UUID> {
    Optional<AuditRecordEntity> findByDomainAndOperationIdAndEventSequence(
            String domain, String operationId, int eventSequence);
}
