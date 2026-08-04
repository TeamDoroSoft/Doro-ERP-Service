package com.dorosoft.erp.table.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableQrCredentialJpaRepository extends JpaRepository<TableQrCredentialEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select credential
              from TableQrCredentialEntity credential
             where credential.tableId = :tableId
               and credential.status = com.dorosoft.erp.table.infrastructure.persistence.TableQrCredentialStatus.ACTIVE
            """)
    Optional<TableQrCredentialEntity> findActiveByTableIdForUpdate(@Param("tableId") UUID tableId);

    Optional<TableQrCredentialEntity> findByTokenDigestAndStatus(
            byte[] tokenDigest, TableQrCredentialStatus status);

    long countByTableIdAndStatus(UUID tableId, TableQrCredentialStatus status);
}
