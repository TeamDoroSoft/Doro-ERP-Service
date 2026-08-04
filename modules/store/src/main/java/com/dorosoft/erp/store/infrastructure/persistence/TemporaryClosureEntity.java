package com.dorosoft.erp.store.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "temporary_closure")
class TemporaryClosureEntity {

    @Id
    @Column(name = "temporary_closure_id", nullable = false)
    private UUID temporaryClosureId;

    @Column(name = "closure_date", nullable = false)
    private LocalDate closureDate;

    @Column(name = "reason", length = 255)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TemporaryClosureEntity() {}

    TemporaryClosureEntity(UUID temporaryClosureId, LocalDate closureDate, String reason) {
        this.temporaryClosureId = temporaryClosureId;
        this.closureDate = closureDate;
        this.reason = reason;
    }

    LocalDate getClosureDate() {
        return closureDate;
    }

    String getReason() {
        return reason;
    }
}
