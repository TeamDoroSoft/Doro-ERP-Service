package com.dorosoft.erp.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class AuditRecordTargetId implements Serializable {

    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "relation_type", nullable = false, length = 30)
    private String relationType;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    protected AuditRecordTargetId() {}

    AuditRecordTargetId(UUID auditId, String relationType, String targetType, String targetId) {
        this.auditId = auditId;
        this.relationType = relationType;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AuditRecordTargetId that)) return false;
        return Objects.equals(auditId, that.auditId)
                && Objects.equals(relationType, that.relationType)
                && Objects.equals(targetType, that.targetType)
                && Objects.equals(targetId, that.targetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auditId, relationType, targetType, targetId);
    }
}
