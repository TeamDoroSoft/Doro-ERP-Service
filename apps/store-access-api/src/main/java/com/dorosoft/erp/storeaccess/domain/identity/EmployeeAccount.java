package com.dorosoft.erp.storeaccess.domain.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EmployeeAccount(
        UUID id,
        UUID tenantId,
        UUID storeId,
        LoginId loginId,
        String passwordHash,
        Role role,
        EmployeeStatus status,
        boolean passwordChangeRequired,
        Instant temporaryPasswordExpiresAt,
        int failedLoginCount,
        Instant lastFailedAt,
        int lockoutLevel,
        Instant lockedUntil,
        Instant lastPasswordAuthenticatedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    private static final Duration TEMPORARY_PASSWORD_VALIDITY = Duration.ofHours(24);

    public EmployeeAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(loginId, "loginId must not be null");
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (failedLoginCount < 0) {
            throw new IllegalArgumentException("failedLoginCount must not be negative");
        }
        if (lockoutLevel < 0) {
            throw new IllegalArgumentException("lockoutLevel must not be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    /** Creates a newly provisioned account with an administrator-issued temporary password (ADR-02-009). */
    public static EmployeeAccount createWithTemporaryPassword(
            UUID id, UUID tenantId, UUID storeId, LoginId loginId, String temporaryPasswordHash, Role role, Instant now) {
        return new EmployeeAccount(
                id,
                tenantId,
                storeId,
                loginId,
                temporaryPasswordHash,
                role,
                EmployeeStatus.ACTIVE,
                true,
                now.plus(TEMPORARY_PASSWORD_VALIDITY),
                0,
                null,
                0,
                null,
                null,
                0L,
                now,
                now);
    }
}
