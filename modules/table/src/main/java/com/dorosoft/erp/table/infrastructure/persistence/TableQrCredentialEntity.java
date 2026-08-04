package com.dorosoft.erp.table.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Entity
@Table(name = "table_qr_credential")
public class TableQrCredentialEntity {

    @Id
    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "token_digest", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] tokenDigest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TableQrCredentialStatus status;

    @Column(name = "predecessor_id")
    private UUID predecessorId;

    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected TableQrCredentialEntity() {}

    private TableQrCredentialEntity(
            UUID credentialId,
            UUID tableId,
            byte[] tokenDigest,
            UUID predecessorId,
            UUID issuedBy,
            Instant issuedAt) {
        this.credentialId = credentialId;
        this.tableId = tableId;
        this.tokenDigest = Arrays.copyOf(tokenDigest, tokenDigest.length);
        this.status = TableQrCredentialStatus.ACTIVE;
        this.predecessorId = predecessorId;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
    }

    public static TableQrCredentialEntity issue(
            UUID credentialId,
            UUID tableId,
            byte[] tokenDigest,
            UUID predecessorId,
            UUID issuedBy,
            Instant issuedAt) {
        return new TableQrCredentialEntity(
                credentialId, tableId, tokenDigest, predecessorId, issuedBy, issuedAt);
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public UUID getTableId() {
        return tableId;
    }

    public byte[] getTokenDigest() {
        return Arrays.copyOf(tokenDigest, tokenDigest.length);
    }

    public TableQrCredentialStatus getStatus() {
        return status;
    }

    public UUID getPredecessorId() {
        return predecessorId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void revoke(UUID revokedBy, Instant revokedAt) {
        if (status == TableQrCredentialStatus.REVOKED) {
            return;
        }
        this.status = TableQrCredentialStatus.REVOKED;
        this.revokedBy = revokedBy;
        this.revokedAt = revokedAt;
    }
}
