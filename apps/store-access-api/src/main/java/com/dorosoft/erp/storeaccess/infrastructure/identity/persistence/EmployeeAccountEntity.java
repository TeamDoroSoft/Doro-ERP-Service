package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
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
@Table(name = "employee_account")
class EmployeeAccountEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmployeeStatus status;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "lockout_level", nullable = false)
    private int lockoutLevel;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_password_authenticated_at")
    private Instant lastPasswordAuthenticatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeAccountEntity() {
    }

    private EmployeeAccountEntity(
            UUID id, UUID tenantId, UUID storeId, String loginId, String passwordHash, Role role,
            EmployeeStatus status, boolean passwordChangeRequired, Instant temporaryPasswordExpiresAt,
            int failedLoginCount, Instant lastFailedAt, int lockoutLevel, Instant lockedUntil,
            Instant lastPasswordAuthenticatedAt, long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.passwordChangeRequired = passwordChangeRequired;
        this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
        this.failedLoginCount = failedLoginCount;
        this.lastFailedAt = lastFailedAt;
        this.lockoutLevel = lockoutLevel;
        this.lockedUntil = lockedUntil;
        this.lastPasswordAuthenticatedAt = lastPasswordAuthenticatedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static EmployeeAccountEntity fromDomain(EmployeeAccount account) {
        return new EmployeeAccountEntity(
                account.id(),
                account.tenantId(),
                account.storeId(),
                account.loginId().value(),
                account.passwordHash(),
                account.role(),
                account.status(),
                account.passwordChangeRequired(),
                account.temporaryPasswordExpiresAt(),
                account.failedLoginCount(),
                account.lastFailedAt(),
                account.lockoutLevel(),
                account.lockedUntil(),
                account.lastPasswordAuthenticatedAt(),
                account.version(),
                account.createdAt(),
                account.updatedAt());
    }

    EmployeeAccount toDomain() {
        return new EmployeeAccount(
                id,
                tenantId,
                storeId,
                new LoginId(loginId),
                passwordHash,
                role,
                status,
                passwordChangeRequired,
                temporaryPasswordExpiresAt,
                failedLoginCount,
                lastFailedAt,
                lockoutLevel,
                lockedUntil,
                lastPasswordAuthenticatedAt,
                version,
                createdAt,
                updatedAt);
    }
}
