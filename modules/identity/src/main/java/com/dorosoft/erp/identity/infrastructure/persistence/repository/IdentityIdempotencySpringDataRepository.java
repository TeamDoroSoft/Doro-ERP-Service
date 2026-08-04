package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.IdentityIdempotencyJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityIdempotencySpringDataRepository
        extends JpaRepository<IdentityIdempotencyJpaEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record from IdentityIdempotencyJpaEntity record
            where record.operation = :operation and record.keyDigest = :keyDigest
            """)
    Optional<IdentityIdempotencyJpaEntity> findForUpdate(
            @Param("operation") IdempotencyOperation operation,
            @Param("keyDigest") byte[] keyDigest
    );

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO identity_idempotency_record (
                idempotency_record_id, operation, key_digest, request_hmac, requested_by,
                status, master_key_version, created_at, expires_at
            ) VALUES (
                :id, :operation, :keyDigest, :requestHmac, :requestedBy,
                'PROCESSING', :masterKeyVersion, :createdAt, :expiresAt
            )
            """, nativeQuery = true)
    int tryInsertProcessing(
            @Param("id") String id,
            @Param("operation") String operation,
            @Param("keyDigest") byte[] keyDigest,
            @Param("requestHmac") byte[] requestHmac,
            @Param("requestedBy") String requestedBy,
            @Param("masterKeyVersion") String masterKeyVersion,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt
    );

    @Modifying
    @Query("""
            delete from IdentityIdempotencyJpaEntity record
            where record.id = :id and record.expiresAt <= :now
            """)
    int deleteExpired(@Param("id") String id, @Param("now") Instant now);
}
