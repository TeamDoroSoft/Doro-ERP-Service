package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.IdentitySecurityEventJpaEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentitySecurityEventSpringDataRepository
        extends JpaRepository<IdentitySecurityEventJpaEntity, String> {
    @Modifying
    @Query(value = """
            INSERT INTO identity_security_event (
                security_event_id, account_id, event_type, outcome,
                failure_class, request_id, occurred_at
            ) VALUES (
                :id, :accountId, :eventType, :outcome,
                :failureClass, :requestId, :occurredAt
            )
            ON DUPLICATE KEY UPDATE security_event_id = security_event_id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") String id,
            @Param("accountId") String accountId,
            @Param("eventType") String eventType,
            @Param("outcome") String outcome,
            @Param("failureClass") String failureClass,
            @Param("requestId") String requestId,
            @Param("occurredAt") Instant occurredAt
    );
}
