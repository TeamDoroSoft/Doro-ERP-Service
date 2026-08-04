package com.dorosoft.erp.identity.application.port;

import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import com.dorosoft.erp.identity.domain.idempotency.IdentityIdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdentityIdempotencyRepository {
    Optional<IdentityIdempotencyRecord> findForUpdate(IdempotencyOperation operation, byte[] keyDigest);

    boolean tryInsertProcessing(IdentityIdempotencyRecord record);

    IdentityIdempotencyRecord save(IdentityIdempotencyRecord record);

    void deleteExpiredLockedRecord(UUID recordId, Instant now);
}
