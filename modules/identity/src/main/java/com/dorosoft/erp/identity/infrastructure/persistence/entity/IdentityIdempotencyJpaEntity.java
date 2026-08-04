package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyStatus;
import com.dorosoft.erp.identity.domain.idempotency.IdentityIdempotencyRecord;
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
public class IdentityIdempotencyJpaEntity {
    @Id
    @Column(name = "idempotency_record_id", nullable = false, columnDefinition = "CHAR(36)")
    private String id;
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 50)
    private IdempotencyOperation operation;
    @Column(name = "key_digest", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] keyDigest;
    @Column(name = "request_hmac", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] requestHmac;
    @Column(name = "requested_by", nullable = false, columnDefinition = "CHAR(36)")
    private String requestedBy;
    @Column(name = "target_account_id", columnDefinition = "CHAR(36)")
    private String targetAccountId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;
    @Column(name = "http_status")
    private Short httpStatus;
    @Column(name = "response_ciphertext", columnDefinition = "BLOB")
    private byte[] responseCiphertext;
    @Column(name = "response_nonce", columnDefinition = "BINARY(12)")
    private byte[] responseNonce;
    @Column(name = "master_key_version", nullable = false, length = 50)
    private String masterKeyVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdentityIdempotencyJpaEntity() {
    }

    public static IdentityIdempotencyJpaEntity fromDomain(IdentityIdempotencyRecord record) {
        IdentityIdempotencyJpaEntity entity = new IdentityIdempotencyJpaEntity();
        entity.id = record.recordId().toString();
        entity.operation = record.operation();
        entity.keyDigest = record.keyDigest();
        entity.requestHmac = record.requestHmac();
        entity.requestedBy = record.requestedBy().toString();
        entity.targetAccountId = record.targetAccountId() == null ? null : record.targetAccountId().toString();
        entity.status = record.status();
        entity.httpStatus = record.httpStatus() == null ? null : record.httpStatus().shortValue();
        entity.responseCiphertext = record.responseCiphertext();
        entity.responseNonce = record.responseNonce();
        entity.masterKeyVersion = record.masterKeyVersion();
        entity.createdAt = record.createdAt();
        entity.completedAt = record.completedAt();
        entity.expiresAt = record.expiresAt();
        return entity;
    }

    public IdentityIdempotencyRecord toDomain() {
        return new IdentityIdempotencyRecord(
                UUID.fromString(id), operation, keyDigest, requestHmac, UUID.fromString(requestedBy),
                targetAccountId == null ? null : UUID.fromString(targetAccountId), status,
                httpStatus == null ? null : httpStatus.intValue(),
                responseCiphertext, responseNonce, masterKeyVersion, createdAt, completedAt, expiresAt
        );
    }

    public String id() { return id; }
}
