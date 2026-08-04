package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import com.dorosoft.erp.identity.domain.account.AccountStatus;
import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import com.dorosoft.erp.identity.domain.account.LoginLockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_account")
public class EmployeeAccountJpaEntity {
    @Id
    @Column(name = "employee_account_id", nullable = false, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "login_id_normalized", nullable = false, length = 50, unique = true)
    private String loginIdNormalized;
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "login_lock_status", nullable = false, length = 20)
    private LoginLockStatus loginLockStatus;
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;
    @Column(name = "temporary_lock_count", nullable = false)
    private int temporaryLockCount;
    @Column(name = "locked_at")
    private Instant lockedAt;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeAccountJpaEntity() {
    }

    public static EmployeeAccountJpaEntity fromDomain(EmployeeAccount account) {
        EmployeeAccountJpaEntity entity = new EmployeeAccountJpaEntity();
        entity.id = account.employeeAccountId().toString();
        entity.loginIdNormalized = account.loginIdNormalized();
        entity.displayName = account.displayName();
        entity.accountStatus = account.accountStatus();
        entity.loginLockStatus = account.loginLockStatus();
        entity.failedLoginCount = account.failedLoginCount();
        entity.temporaryLockCount = account.temporaryLockCount();
        entity.lockedAt = account.lockedAt();
        entity.lockedUntil = account.lockedUntil();
        entity.version = account.version();
        return entity;
    }

    public EmployeeAccount toDomain() {
        return new EmployeeAccount(
                UUID.fromString(id), loginIdNormalized, displayName, accountStatus, loginLockStatus,
                failedLoginCount, temporaryLockCount, lockedAt, lockedUntil, version
        );
    }

    public String id() { return id; }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
