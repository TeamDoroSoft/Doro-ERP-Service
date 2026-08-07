package com.dorosoft.erp.storeaccess.domain.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private static final Duration OBSERVATION_WINDOW = Duration.ofMinutes(15);
    private static final int LOCK_THRESHOLD = 5;
    private static final int MAX_LOCKOUT_LEVEL = 5;
    private static final List<Duration> LOCKOUT_DURATIONS_BY_LEVEL = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(4),
            Duration.ofMinutes(8),
            Duration.ofMinutes(15));

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

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Applies one authentication failure (ADR-02-006). A call while still locked is a no-op: locked requests
     * must not reach here without first checking {@link #isLocked(Instant)}, and this guard keeps the
     * aggregate's own invariant regardless of caller behavior.
     */
    public EmployeeAccount recordFailedLogin(Instant now) {
        if (isLocked(now)) {
            return this;
        }

        boolean observationWindowExpired = lastFailedAt == null
                || Duration.between(lastFailedAt, now).compareTo(OBSERVATION_WINDOW) > 0;
        int effectiveCount = observationWindowExpired ? 0 : failedLoginCount;
        int effectiveLevel = observationWindowExpired ? 0 : lockoutLevel;
        int newCount = effectiveCount + 1;

        if (effectiveLevel == 0 && newCount < LOCK_THRESHOLD) {
            return new EmployeeAccount(
                    id, tenantId, storeId, loginId, passwordHash, role, status, passwordChangeRequired,
                    temporaryPasswordExpiresAt, newCount, now, 0, null, lastPasswordAuthenticatedAt, version,
                    createdAt, now);
        }

        int newLevel = Math.min(effectiveLevel + 1, MAX_LOCKOUT_LEVEL);
        Instant newLockedUntil = now.plus(LOCKOUT_DURATIONS_BY_LEVEL.get(newLevel - 1));
        return new EmployeeAccount(
                id, tenantId, storeId, loginId, passwordHash, role, status, passwordChangeRequired,
                temporaryPasswordExpiresAt, newCount, now, newLevel, newLockedUntil, lastPasswordAuthenticatedAt,
                version, createdAt, now);
    }

    /** Resets failure/lockout state and refreshes the last successful password authentication time. */
    public EmployeeAccount recordSuccessfulLogin(Instant now) {
        return new EmployeeAccount(
                id, tenantId, storeId, loginId, passwordHash, role, status, passwordChangeRequired,
                temporaryPasswordExpiresAt, 0, null, 0, null, now, version, createdAt, now);
    }
}
