package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_security_history")
class EmployeeSecurityHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private SecurityHistoryEventType eventType;

    @Column(name = "actor_employee_id")
    private UUID actorEmployeeId;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private SecurityHistoryResult result;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "previous_value", length = 50)
    private String previousValue;

    @Column(name = "new_value", length = 50)
    private String newValue;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected EmployeeSecurityHistoryEntity() {
    }

    private EmployeeSecurityHistoryEntity(
            UUID id, UUID tenantId, UUID storeId, SecurityHistoryEventType eventType, UUID actorEmployeeId,
            String targetType, UUID targetId, SecurityHistoryResult result, String reasonCode,
            String previousValue, String newValue, Instant occurredAt, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.eventType = eventType;
        this.actorEmployeeId = actorEmployeeId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.reasonCode = reasonCode;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
        this.expiresAt = expiresAt;
    }

    static EmployeeSecurityHistoryEntity fromDomain(EmployeeSecurityHistory history) {
        return new EmployeeSecurityHistoryEntity(
                history.id(),
                history.tenantId(),
                history.storeId(),
                history.eventType(),
                history.actorEmployeeId(),
                history.targetType(),
                history.targetId(),
                history.result(),
                history.reasonCode(),
                history.previousValue(),
                history.newValue(),
                history.occurredAt(),
                history.expiresAt());
    }

    EmployeeSecurityHistory toDomain() {
        return new EmployeeSecurityHistory(
                id, tenantId, storeId, eventType, actorEmployeeId, targetType, targetId, result, reasonCode,
                previousValue, newValue, occurredAt, expiresAt);
    }
}
