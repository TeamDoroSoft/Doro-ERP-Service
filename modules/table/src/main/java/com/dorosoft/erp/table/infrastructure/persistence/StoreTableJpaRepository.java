package com.dorosoft.erp.table.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreTableJpaRepository extends JpaRepository<StoreTableEntity, UUID> {

    Optional<StoreTableEntity> findByNormalizedNumber(String normalizedNumber);

    List<StoreTableEntity> findAllByOrderByNormalizedNumberAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select table from StoreTableEntity table where table.tableId = :tableId")
    Optional<StoreTableEntity> findByIdForUpdate(@Param("tableId") UUID tableId);
}
