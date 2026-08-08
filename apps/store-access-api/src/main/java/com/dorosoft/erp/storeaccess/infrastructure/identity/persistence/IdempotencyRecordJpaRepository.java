package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRecordJpaRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {

    Optional<IdempotencyRecordEntity> findByTenantIdAndActorEmployeeIdAndOperationAndKeyDigest(
            UUID tenantId, UUID actorEmployeeId, String operation, String keyDigest);
}
