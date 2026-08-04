package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import com.dorosoft.erp.identity.domain.securityevent.IdentitySecurityEvent;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventFailureClass;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventOutcome;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_security_event")
public class IdentitySecurityEventJpaEntity {
    @Id
    @Column(name = "security_event_id", nullable = false, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "account_id", columnDefinition = "CHAR(36)")
    private String accountId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private SecurityEventType eventType;
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private SecurityEventOutcome outcome;
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_class", length = 60)
    private SecurityEventFailureClass failureClass;
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;
    @Column(name = "occurred_at", nullable = false)
    private java.time.Instant occurredAt;

    protected IdentitySecurityEventJpaEntity() {
    }

    public static IdentitySecurityEventJpaEntity fromDomain(IdentitySecurityEvent event) {
        IdentitySecurityEventJpaEntity entity = new IdentitySecurityEventJpaEntity();
        entity.id = event.securityEventId().toString();
        entity.accountId = event.accountId() == null ? null : event.accountId().toString();
        entity.eventType = event.eventType();
        entity.outcome = event.outcome();
        entity.failureClass = event.failureClass();
        entity.requestId = event.requestId();
        entity.occurredAt = event.occurredAt();
        return entity;
    }
}
