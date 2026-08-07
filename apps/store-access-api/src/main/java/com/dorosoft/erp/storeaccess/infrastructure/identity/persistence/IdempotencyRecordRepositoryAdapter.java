package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.application.port.identity.IdempotencyRecordRepository;
import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class IdempotencyRecordRepositoryAdapter implements IdempotencyRecordRepository {

    private final IdempotencyRecordJpaRepository jpaRepository;

    IdempotencyRecordRepositoryAdapter(IdempotencyRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<IdempotencyRecord> findByScope(
            UUID tenantId, UUID actorEmployeeId, String operation, String keyDigest) {
        return jpaRepository
                .findByTenantIdAndActorEmployeeIdAndOperationAndKeyDigest(tenantId, actorEmployeeId, operation, keyDigest)
                .map(IdempotencyRecordEntity::toDomain);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        IdempotencyRecordEntity saved = jpaRepository.saveAndFlush(IdempotencyRecordEntity.fromDomain(record));
        return saved.toDomain();
    }
}
