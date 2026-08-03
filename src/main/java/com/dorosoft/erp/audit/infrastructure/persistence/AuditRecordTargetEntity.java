package com.dorosoft.erp.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_record_target")
class AuditRecordTargetEntity {

    @EmbeddedId private AuditRecordTargetId id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditRecordTargetEntity() {}

    AuditRecordTargetEntity(AuditRecordTargetId id, Instant occurredAt) {
        this.id = id;
        this.occurredAt = occurredAt;
    }
}
