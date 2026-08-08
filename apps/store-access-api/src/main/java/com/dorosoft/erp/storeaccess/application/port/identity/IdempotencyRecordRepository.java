package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository {

    Optional<IdempotencyRecord> findByScope(UUID tenantId, UUID actorEmployeeId, String operation, String keyDigest);

    IdempotencyRecord save(IdempotencyRecord record);
}
