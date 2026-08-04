package com.dorosoft.erp.table.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "store_table")
public class StoreTableEntity {

    @Id
    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "table_number", nullable = false, length = 20)
    private String tableNumber;

    @Column(name = "normalized_number", nullable = false, length = 20)
    private String normalizedNumber;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "seat_capacity", nullable = false)
    private short seatCapacity;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StoreTableEntity() {}

    private StoreTableEntity(
            UUID tableId,
            String tableNumber,
            String normalizedNumber,
            String displayName,
            int seatCapacity,
            boolean active,
            Instant now) {
        this.tableId = tableId;
        this.tableNumber = tableNumber;
        this.normalizedNumber = normalizedNumber;
        this.displayName = displayName;
        this.seatCapacity = (short) seatCapacity;
        this.active = active;
        this.updatedAt = now;
    }

    public static StoreTableEntity create(
            UUID tableId,
            String tableNumber,
            String normalizedNumber,
            String displayName,
            int seatCapacity,
            boolean active) {
        return new StoreTableEntity(
                tableId, tableNumber, normalizedNumber, displayName, seatCapacity, active, Instant.now());
    }

    public UUID getTableId() {
        return tableId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSeatCapacity() {
        return seatCapacity;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public void applyDetails(
            String tableNumber, String normalizedNumber, String displayName, int seatCapacity) {
        this.tableNumber = tableNumber;
        this.normalizedNumber = normalizedNumber;
        this.displayName = displayName;
        this.seatCapacity = (short) seatCapacity;
        this.updatedAt = Instant.now();
    }

    public void applyActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
