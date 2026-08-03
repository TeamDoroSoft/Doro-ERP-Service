package com.dorosoft.erp.audit.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_record")
class AuditRecordEntity {

    @Id @Column(name = "audit_id", nullable = false) private UUID auditId;
    @Column(name = "domain", nullable = false, length = 30) private String domain;
    @Column(name = "action", nullable = false, length = 80) private String action;
    @Column(name = "operation_id", nullable = false, length = 64) private String operationId;
    @Column(name = "event_sequence", nullable = false) private int eventSequence;
    @Column(name = "actor_type", nullable = false, length = 30) private String actorType;
    @Column(name = "actor_id", nullable = false, length = 100) private String actorId;
    @Column(name = "actor_role_snapshot", length = 40) private String actorRoleSnapshot;
    @Column(name = "actor_display_name_snapshot", length = 100) private String actorDisplayNameSnapshot;
    @Column(name = "target_type", nullable = false, length = 50) private String targetType;
    @Column(name = "target_id", nullable = false, length = 100) private String targetId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", nullable = false, columnDefinition = "json") private String beforeValue;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", nullable = false, columnDefinition = "json") private String afterValue;
    @Column(name = "reason_code", length = 50) private String reasonCode;
    @Column(name = "reason", length = 500) private String reason;
    @Column(name = "value_schema_version", nullable = false, length = 20) private String valueSchemaVersion;
    @Column(name = "retention_class", nullable = false, length = 40) private String retentionClass;
    @Column(name = "retention_until", nullable = false) private Instant retentionUntil;
    @Column(name = "payload_hmac", nullable = false, columnDefinition = "BINARY(32)") private byte[] payloadHmac;
    @Column(name = "request_id", nullable = false, length = 100) private String requestId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "audit_id", nullable = false, insertable = false, updatable = false)
    private List<AuditRecordTargetEntity> targets = new ArrayList<>();

    protected AuditRecordEntity() {}

    AuditRecordEntity(UUID auditId, String domain, String action, String operationId, int eventSequence,
            String actorType, String actorId, String actorRoleSnapshot, String targetType, String targetId,
            String beforeValue, String afterValue, String reasonCode, String reason, String valueSchemaVersion,
            String retentionClass, Instant retentionUntil, byte[] payloadHmac, String requestId,
            Instant occurredAt, Instant recordedAt) {
        this.auditId = auditId;
        this.domain = domain;
        this.action = action;
        this.operationId = operationId;
        this.eventSequence = eventSequence;
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorRoleSnapshot = actorRoleSnapshot;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.reasonCode = reasonCode;
        this.reason = reason;
        this.valueSchemaVersion = valueSchemaVersion;
        this.retentionClass = retentionClass;
        this.retentionUntil = retentionUntil;
        this.payloadHmac = payloadHmac;
        this.requestId = requestId;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
    }

    UUID getAuditId() { return auditId; }
    byte[] getPayloadHmac() { return payloadHmac; }
    Instant getOccurredAt() { return occurredAt; }
    List<AuditRecordTargetEntity> getTargets() { return targets; }
}
