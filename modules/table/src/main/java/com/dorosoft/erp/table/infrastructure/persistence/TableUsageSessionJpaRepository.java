package com.dorosoft.erp.table.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
