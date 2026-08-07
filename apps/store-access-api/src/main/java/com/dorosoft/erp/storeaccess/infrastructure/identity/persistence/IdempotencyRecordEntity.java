package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecord;
import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_idempotency_record")
class IdempotencyRecordEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_employee_id", nullable = false)
    private UUID actorEmployeeId;

    @Column(name = "operation", nullable = false, length = 50)
    private String operation;

    @Column(name = "key_digest", nullable = false, length = 255)
    private String keyDigest;

    @Column(name = "request_hmac", nullable = false, length = 255)
    private String requestHmac;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyRecordStatus status;

    @Column(name = "result_payload")
    private String resultPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecordEntity() {
    }

    private IdempotencyRecordEntity(
            UUID id, UUID tenantId, UUID actorEmployeeId, String operation, String keyDigest, String requestHmac,
            IdempotencyRecordStatus status, String resultPayload, Instant createdAt, Instant completedAt,
            Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorEmployeeId = actorEmployeeId;
        this.operation = operation;
        this.keyDigest = keyDigest;
        this.requestHmac = requestHmac;
        this.status = status;
        this.resultPayload = resultPayload;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.expiresAt = expiresAt;
    }

    static IdempotencyRecordEntity fromDomain(IdempotencyRecord record) {
        return new IdempotencyRecordEntity(
                record.id(),
                record.tenantId(),
                record.actorEmployeeId(),
                record.operation(),
                record.keyDigest(),
                record.requestHmac(),
                record.status(),
                record.resultPayload(),
                record.createdAt(),
                record.completedAt(),
                record.expiresAt());
    }

    IdempotencyRecord toDomain() {
        return new IdempotencyRecord(
                id, tenantId, actorEmployeeId, operation, keyDigest, requestHmac, status, resultPayload,
                createdAt, completedAt, expiresAt);
    }
}
