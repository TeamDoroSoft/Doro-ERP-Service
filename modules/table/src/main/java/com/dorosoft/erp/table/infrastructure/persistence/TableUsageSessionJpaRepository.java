package com.dorosoft.erp.table.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TableUsageSessionJpaRepository extends JpaRepository<TableUsageSessionEntity, UUID> {

    boolean existsByTableIdAndStatus(UUID tableId, TableUsageSessionStatus status);

    @Query(
            """
            select session.tableId
            from TableUsageSessionEntity session
            where session.status = :status
              and session.tableId in :tableIds
            """)
    List<UUID> findTableIdsByStatusAndTableIdIn(
            @Param("status") TableUsageSessionStatus status, @Param("tableIds") Collection<UUID> tableIds);

    @Query(
            """
            select session.sessionId
            from TableUsageSessionEntity session
            where session.tableId = :tableId
              and session.status = com.dorosoft.erp.table.infrastructure.persistence.TableUsageSessionStatus.OPEN
            """)
    Optional<UUID> findOpenSessionIdByTableId(@Param("tableId") UUID tableId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select session
            from TableUsageSessionEntity session
            where session.tableId = :tableId
              and session.sessionId = :sessionId
            """)
    Optional<TableUsageSessionEntity> findByTableIdAndSessionIdForUpdate(
            @Param("tableId") UUID tableId, @Param("sessionId") UUID sessionId);
}
