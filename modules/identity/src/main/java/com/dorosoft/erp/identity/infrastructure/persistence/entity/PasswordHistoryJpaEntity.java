package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import com.dorosoft.erp.identity.domain.credential.PasswordHistoryEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credential_password_history")
public class PasswordHistoryJpaEntity {
    @Id
    @Column(name = "password_history_id", nullable = false, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "employee_account_id", nullable = false, columnDefinition = "CHAR(36)")
    private String accountId;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected PasswordHistoryJpaEntity() {
    }

    public static PasswordHistoryJpaEntity fromDomain(PasswordHistoryEntry entry) {
        PasswordHistoryJpaEntity entity = new PasswordHistoryJpaEntity();
        entity.id = entry.passwordHistoryId().toString();
        entity.accountId = entry.employeeAccountId().toString();
        entity.passwordHash = entry.passwordHash();
        entity.changedAt = entry.changedAt();
        return entity;
    }

    public PasswordHistoryEntry toDomain() {
        return new PasswordHistoryEntry(
                UUID.fromString(id), UUID.fromString(accountId), passwordHash, changedAt
        );
    }

    public String id() { return id; }
}
