package com.dorosoft.erp.commerce.infrastructure.persistence.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "destination", nullable = false, length = 80)
    private String destination;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "message_group", nullable = false, length = 120)
    private String messageGroup;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {
    }

    OutboxEventEntity(
            UUID id,
            UUID eventId,
            String destination,
            String eventType,
            int eventVersion,
            String aggregateType,
            UUID aggregateId,
            UUID tenantId,
            UUID storeId,
            String messageGroup,
            String payload,
            String status,
            Instant occurredAt) {
        this.id = id;
        this.eventId = eventId;
        this.destination = destination;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.messageGroup = messageGroup;
        this.payload = payload;
        this.status = status;
        this.attemptCount = 0;
        this.nextAttemptAt = occurredAt;
        this.occurredAt = occurredAt;
        this.createdAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getDestination() {
        return destination;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public String getMessageGroup() {
        return messageGroup;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
