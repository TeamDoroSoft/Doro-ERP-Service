package com.dorosoft.erp.identity.domain.account;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 계정 도메인 Aggregate.
 */
public final class EmployeeAccount {
    private static final int MAX_FAILURE_COUNT = 5;
    private static final Duration TEMP_LOCK_DURATION = Duration.ofMinutes(5);

    private final UUID employeeAccountId;
    private final String loginIdNormalized;
    private final String displayName;
    private final AccountStatus accountStatus;
    private final LoginLockStatus loginLockStatus;
    private final int failedLoginCount;
    private final int temporaryLockCount;
    private final Instant lockedAt;
    private final Instant lockedUntil;
    private final long version;

    public EmployeeAccount(
            UUID employeeAccountId,
            String loginIdNormalized,
            String displayName,
            AccountStatus accountStatus,
            LoginLockStatus loginLockStatus,
            int failedLoginCount,
            int temporaryLockCount,
            Instant lockedAt,
            Instant lockedUntil,
            long version
    ) {
        this.employeeAccountId = Objects.requireNonNull(employeeAccountId, "employeeAccountId");
        this.loginIdNormalized = LoginId.fromNormalized(loginIdNormalized).value();
        this.displayName = DisplayName.of(displayName).value();
        this.accountStatus = Objects.requireNonNull(accountStatus, "accountStatus");
        this.loginLockStatus = Objects.requireNonNull(loginLockStatus, "loginLockStatus");
        if (failedLoginCount < 0) {
            throw new IllegalArgumentException("failedLoginCount must be greater than or equal to 0");
        }
        if (temporaryLockCount < 0 || temporaryLockCount > 1) {
            throw new IllegalArgumentException("temporaryLockCount must be 0 or 1");
        }
        if (loginLockStatus == LoginLockStatus.NONE) {
            if (lockedAt != null || lockedUntil != null) {
                throw new IllegalArgumentException("NONE lock must not have lock timestamps");
            }
        }
        if (loginLockStatus == LoginLockStatus.TEMPORARY) {
            if (lockedAt == null || lockedUntil == null) {
                throw new IllegalArgumentException("TEMPORARY lock requires lockedAt and lockedUntil");
            }
            if (!lockedUntil.equals(lockedAt.plus(TEMP_LOCK_DURATION))) {
                throw new IllegalArgumentException("TEMPORARY lock must last exactly five minutes");
            }
        }
        if (loginLockStatus == LoginLockStatus.PERMANENT) {
            if (lockedAt == null || lockedUntil != null) {
                throw new IllegalArgumentException("PERMANENT lock requires lockedAt and no lockedUntil");
            }
        }
        this.failedLoginCount = failedLoginCount;
        this.temporaryLockCount = temporaryLockCount;
        this.lockedAt = lockedAt;
        this.lockedUntil = lockedUntil;
        if (version < 0) {
            throw new IllegalArgumentException("version must be greater than or equal to 0");
        }
        this.version = version;
    }

    public static EmployeeAccount bootstrapActiveAccount(
            UUID employeeAccountId,
            String loginIdNormalized,
            String displayName
    ) {
        return new EmployeeAccount(
                employeeAccountId,
                loginIdNormalized,
                displayName,
                AccountStatus.ACTIVE,
                LoginLockStatus.NONE,
                0,
                0,
                null,
                null,
                0L
        );
    }

    public EmployeeAccount withFailedLoginAttempt(Instant attemptAt) {
        EmployeeAccount resolved = clearTemporaryIfExpired(attemptAt);
        if (!resolved.isActive()) {
            return resolved; // 비활성/정지 계정은 로직에서 별도 처리
        }
        if (resolved.loginLockStatus == LoginLockStatus.PERMANENT) {
            return resolved;
        }
        if (resolved.loginLockStatus == LoginLockStatus.TEMPORARY) {
            return resolved;
        }

        int nextFailed = resolved.failedLoginCount + 1;
        if (nextFailed >= MAX_FAILURE_COUNT) {
            if (resolved.temporaryLockCount == 0) {
                return resolved.lockTemporary(attemptAt);
            }
            return resolved.lockPermanent(attemptAt);
        }
        return new EmployeeAccount(
                resolved.employeeAccountId,
                resolved.loginIdNormalized,
                resolved.displayName,
                resolved.accountStatus,
                LoginLockStatus.NONE,
                nextFailed,
                resolved.temporaryLockCount,
                null,
                null,
                resolved.version
        );
    }

    public EmployeeAccount withSuccessfulLogin() {
        if (!isActive()) {
            throw new IllegalStateException("Cannot login on inactive account");
        }
        if (loginLockStatus != LoginLockStatus.NONE) {
            throw new IllegalStateException("Cannot login on locked account");
        }
        return new EmployeeAccount(
                employeeAccountId,
                loginIdNormalized,
                displayName,
                AccountStatus.ACTIVE,
                LoginLockStatus.NONE,
                0,
                0,
                null,
                null,
                version
        );
    }

    public EmployeeAccount withAdministratorUnlock() {
        if (loginLockStatus == LoginLockStatus.NONE) {
            return this;
        }
        return new EmployeeAccount(
                employeeAccountId,
                loginIdNormalized,
                displayName,
                accountStatus,
                LoginLockStatus.NONE,
                0,
                0,
                null,
                null,
                version
        );
    }

    public EmployeeAccount withAccountStatus(AccountStatus next) {
        Objects.requireNonNull(next, "next");
        if (accountStatus == next) {
            return this;
        }
        return new EmployeeAccount(
                employeeAccountId,
                loginIdNormalized,
                displayName,
                next,
                loginLockStatus,
                failedLoginCount,
                temporaryLockCount,
                lockedAt,
                lockedUntil,
                version
        );
    }

    public EmployeeAccount deactivate() {
        return withAccountStatus(AccountStatus.INACTIVE);
    }

    public EmployeeAccount activate() {
        return withAccountStatus(AccountStatus.ACTIVE);
    }

    public EmployeeAccount clearTemporaryIfExpired(Instant now) {
        if (loginLockStatus != LoginLockStatus.TEMPORARY || lockedUntil == null || now.isBefore(lockedUntil)) {
            return this;
        }
        return new EmployeeAccount(
                employeeAccountId,
                loginIdNormalized,
                displayName,
                accountStatus,
                LoginLockStatus.NONE,
                0,
                1,
                null,
                null,
                version
        );
    }

    public boolean isActive() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        if (loginLockStatus == LoginLockStatus.PERMANENT) {
            return true;
        }
        if (loginLockStatus == LoginLockStatus.TEMPORARY && lockedUntil != null) {
            return now.isBefore(lockedUntil);
        }
        return false;
    }

    public UUID employeeAccountId() {
        return employeeAccountId;
    }

    public String loginIdNormalized() {
        return loginIdNormalized;
    }

    public String displayName() {
        return displayName;
    }

    public AccountStatus accountStatus() {
        return accountStatus;
    }

    public LoginLockStatus loginLockStatus() {
        return loginLockStatus;
    }

    public int failedLoginCount() {
        return failedLoginCount;
    }

    public int temporaryLockCount() {
        return temporaryLockCount;
    }

    public Instant lockedAt() {
        return lockedAt;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public long version() {
        return version;
    }

    private EmployeeAccount lockTemporary(Instant now) {
        return new EmployeeAccount(
                employeeAccountId,
                loginIdNormalized,
                displayName,
                AccountStatus.ACTIVE,
                LoginLockStatus.TEMPORARY,
                MAX_FAILURE_COUNT,
                1,
                now,
                now.plus(TEMP_LOCK_DURATION),
                version
        );
    }

    private EmployeeAccount lockPermanent(Instant now) {
        return new EmployeeAccount(
                employeeAccountId,
                loginIdNormalized,
                displayName,
                AccountStatus.ACTIVE,
                LoginLockStatus.PERMANENT,
                MAX_FAILURE_COUNT,
                Math.max(1, temporaryLockCount),
                now,
                null,
                version
        );
    }
}
