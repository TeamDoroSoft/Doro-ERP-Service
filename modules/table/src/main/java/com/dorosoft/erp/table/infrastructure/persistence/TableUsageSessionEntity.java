package com.dorosoft.erp.table.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "table_usage_session")
class TableUsageSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TableUsageSessionStatus status;

    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason", length = 40)
    private String closeReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TableUsageSessionEntity() {}

    private TableUsageSessionEntity(UUID sessionId, UUID tableId, UUID openedBy, Instant openedAt) {
        this.sessionId = sessionId;
        this.tableId = tableId;
        this.status = TableUsageSessionStatus.OPEN;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
    }

    static TableUsageSessionEntity open(UUID sessionId, UUID tableId, UUID openedBy, Instant openedAt) {
        return new TableUsageSessionEntity(sessionId, tableId, openedBy, openedAt);
    }

    UUID getSessionId() {
        return sessionId;
    }

    UUID getTableId() {
        return tableId;
    }

    TableUsageSessionStatus getStatus() {
        return status;
    }

    Instant getOpenedAt() {
        return openedAt;
    }

    long getVersion() {
        return version;
    }
}
