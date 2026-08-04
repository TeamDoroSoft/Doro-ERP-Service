package com.dorosoft.erp.identity.infrastructure.persistence;

import com.dorosoft.erp.identity.application.port.IdentityIdempotencyRepository;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyStatus;
import com.dorosoft.erp.identity.domain.idempotency.IdentityIdempotencyRecord;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.IdentityIdempotencyJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.IdentityIdempotencySpringDataRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaIdentityIdempotencyRepositoryAdapter implements IdentityIdempotencyRepository {
    private final IdentityIdempotencySpringDataRepository repository;

    public JpaIdentityIdempotencyRepositoryAdapter(IdentityIdempotencySpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<IdentityIdempotencyRecord> findForUpdate(
            IdempotencyOperation operation,
            byte[] keyDigest
    ) {
        return repository.findForUpdate(operation, keyDigest).map(IdentityIdempotencyJpaEntity::toDomain);
    }

    @Override
    public boolean tryInsertProcessing(IdentityIdempotencyRecord record) {
        if (record.status() != IdempotencyStatus.PROCESSING) {
            throw new IllegalArgumentException("only PROCESSING state can be inserted as a claim");
        }
        return repository.tryInsertProcessing(
                record.recordId().toString(), record.operation().name(), record.keyDigest(),
                record.requestHmac(), record.requestedBy().toString(), record.masterKeyVersion(),
                record.createdAt(), record.expiresAt()
        ) == 1;
    }

    @Override
    public IdentityIdempotencyRecord save(IdentityIdempotencyRecord record) {
        return repository.saveAndFlush(IdentityIdempotencyJpaEntity.fromDomain(record)).toDomain();
    }

    @Override
    public void deleteExpiredLockedRecord(UUID recordId, Instant now) {
        if (repository.deleteExpired(recordId.toString(), now) != 1) {
            throw new IllegalStateException("idempotency record is not expired or no longer locked");
        }
    }
}
