package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeSecurityHistoryRepository {

    Optional<EmployeeSecurityHistory> findById(UUID id);

    EmployeeSecurityHistory save(EmployeeSecurityHistory history);

    /** Newest-first, per {@link SecurityHistoryQuery} (ADR-02-015). Excludes logically expired records. */
    List<EmployeeSecurityHistory> search(SecurityHistoryQuery query);

    /**
     * Deletes up to {@code batchSize} records whose {@code expiresAt} is at or before {@code now}, returning
     * the number deleted (ADR-02-015: "매일 제한된 Batch로 삭제").
     */
    int deleteExpiredBatch(Instant now, int batchSize);
}
