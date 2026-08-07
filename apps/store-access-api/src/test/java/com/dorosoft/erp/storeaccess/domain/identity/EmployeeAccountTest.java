package com.dorosoft.erp.storeaccess.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeAccountTest {

    private final Instant now = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void createWithTemporaryPasswordStartsActiveAndRequiresPasswordChange() {
        EmployeeAccount account = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new LoginId("owner01"), "argon2-hash", Role.OWNER, now);

        assertThat(account.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(account.passwordChangeRequired()).isTrue();
        assertThat(account.temporaryPasswordExpiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
        assertThat(account.failedLoginCount()).isZero();
        assertThat(account.lockoutLevel()).isZero();
        assertThat(account.version()).isZero();
        assertThat(account.createdAt()).isEqualTo(now);
        assertThat(account.updatedAt()).isEqualTo(now);
    }

    @Test
    void rejectsBlankPasswordHash() {
        assertThatThrownBy(() -> new EmployeeAccount(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new LoginId("owner01"),
                " ", Role.OWNER, EmployeeStatus.ACTIVE, false, null, 0, null, 0, null, null, 0L, now, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFailedLoginCount() {
        assertThatThrownBy(() -> new EmployeeAccount(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new LoginId("owner01"),
                "argon2-hash", Role.OWNER, EmployeeStatus.ACTIVE, false, null, -1, null, 0, null, null, 0L, now, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeLockoutLevel() {
        assertThatThrownBy(() -> new EmployeeAccount(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new LoginId("owner01"),
                "argon2-hash", Role.OWNER, EmployeeStatus.ACTIVE, false, null, 0, null, -1, null, null, 0L, now, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordFailedLoginIncrementsCountWithoutLockingBelowThreshold() {
        EmployeeAccount account = activeAccount();

        for (int i = 1; i <= 4; i++) {
            account = account.recordFailedLogin(now.plusSeconds(i));
        }

        assertThat(account.failedLoginCount()).isEqualTo(4);
        assertThat(account.lockoutLevel()).isZero();
        assertThat(account.lockedUntil()).isNull();
        assertThat(account.isLocked(now.plusSeconds(4))).isFalse();
    }

    @Test
    void fifthFailureWithinObservationWindowLocksForOneMinute() {
        EmployeeAccount account = activeAccount();
        Instant fifthFailureAt = now.plusSeconds(5);

        for (int i = 1; i <= 5; i++) {
            account = account.recordFailedLogin(now.plusSeconds(i));
        }

        assertThat(account.failedLoginCount()).isEqualTo(5);
        assertThat(account.lockoutLevel()).isEqualTo(1);
        assertThat(account.lockedUntil()).isEqualTo(fifthFailureAt.plus(Duration.ofMinutes(1)));
        assertThat(account.isLocked(fifthFailureAt)).isTrue();
        assertThat(account.isLocked(fifthFailureAt.plus(Duration.ofMinutes(1)))).isFalse();
    }

    @Test
    void failedLoginWhileLockedIsANoOp() {
        EmployeeAccount locked = lockedAtLevelOne();

        EmployeeAccount unchanged = locked.recordFailedLogin(locked.lockedUntil().minusSeconds(1));

        assertThat(unchanged).isEqualTo(locked);
    }

    @Test
    void failureAfterLockExpiresEscalatesToNextLevel() {
        EmployeeAccount locked = lockedAtLevelOne();
        Instant nextFailureAt = locked.lockedUntil().plusSeconds(1);

        EmployeeAccount escalated = locked.recordFailedLogin(nextFailureAt);

        assertThat(escalated.lockoutLevel()).isEqualTo(2);
        assertThat(escalated.lockedUntil()).isEqualTo(nextFailureAt.plus(Duration.ofMinutes(2)));
    }

    @Test
    void lockoutDurationCapsAtFifteenMinutesFromLevelFiveOnward() {
        // Fails exactly at the moment each lock expires, the tightest gap for which recordFailedLogin still
        // treats the account as unlocked (isLocked uses a strict "before") and within the 15-minute
        // observation window (the window check is strictly-greater-than 15 minutes).
        EmployeeAccount account = lockedAtLevelOne();
        int[] expectedMinutesByLevel = {2, 4, 8, 15, 15};

        for (int expectedMinutes : expectedMinutesByLevel) {
            Instant nextFailureAt = account.lockedUntil();
            account = account.recordFailedLogin(nextFailureAt);
            assertThat(account.lockedUntil()).isEqualTo(nextFailureAt.plus(Duration.ofMinutes(expectedMinutes)));
        }
        assertThat(account.lockoutLevel()).isEqualTo(5);
    }

    @Test
    void failureAfterObservationWindowExpiresResetsCountAndLevel() {
        EmployeeAccount locked = lockedAtLevelOne();
        Instant longAfterLastFailure = locked.lastFailedAt().plus(Duration.ofMinutes(16));

        EmployeeAccount reset = locked.recordFailedLogin(longAfterLastFailure);

        assertThat(reset.failedLoginCount()).isEqualTo(1);
        assertThat(reset.lockoutLevel()).isZero();
        assertThat(reset.lockedUntil()).isNull();
    }

    @Test
    void recordSuccessfulLoginResetsLockoutStateAndRefreshesLastAuthenticatedAt() {
        EmployeeAccount locked = lockedAtLevelOne();
        Instant loginAt = locked.lockedUntil().plusSeconds(1);

        EmployeeAccount succeeded = locked.recordSuccessfulLogin(loginAt);

        assertThat(succeeded.failedLoginCount()).isZero();
        assertThat(succeeded.lockoutLevel()).isZero();
        assertThat(succeeded.lockedUntil()).isNull();
        assertThat(succeeded.lastPasswordAuthenticatedAt()).isEqualTo(loginAt);
    }

    @Test
    void changeRoleReplacesRoleAndUpdatesTimestampWithoutTouchingOtherFields() {
        EmployeeAccount account = activeAccount();
        Instant changedAt = now.plusSeconds(30);

        EmployeeAccount changed = account.changeRole(Role.MANAGER, changedAt);

        assertThat(changed.role()).isEqualTo(Role.MANAGER);
        assertThat(changed.updatedAt()).isEqualTo(changedAt);
        assertThat(changed.status()).isEqualTo(account.status());
        assertThat(changed.passwordHash()).isEqualTo(account.passwordHash());
        assertThat(changed.createdAt()).isEqualTo(account.createdAt());
    }

    @Test
    void changeStatusReplacesStatusAndUpdatesTimestampWithoutTouchingOtherFields() {
        EmployeeAccount account = activeAccount();
        Instant changedAt = now.plusSeconds(30);

        EmployeeAccount changed = account.changeStatus(EmployeeStatus.INACTIVE, changedAt);

        assertThat(changed.status()).isEqualTo(EmployeeStatus.INACTIVE);
        assertThat(changed.updatedAt()).isEqualTo(changedAt);
        assertThat(changed.role()).isEqualTo(account.role());
        assertThat(changed.passwordHash()).isEqualTo(account.passwordHash());
        assertThat(changed.createdAt()).isEqualTo(account.createdAt());
    }

    @Test
    void changePasswordClearsTemporaryPasswordStateAndRefreshesLastAuthenticatedAt() {
        EmployeeAccount account = activeAccount();
        Instant changedAt = now.plusSeconds(30);

        EmployeeAccount changed = account.changePassword("new-argon2-hash", changedAt);

        assertThat(changed.passwordHash()).isEqualTo("new-argon2-hash");
        assertThat(changed.passwordChangeRequired()).isFalse();
        assertThat(changed.temporaryPasswordExpiresAt()).isNull();
        assertThat(changed.lastPasswordAuthenticatedAt()).isEqualTo(changedAt);
        assertThat(changed.updatedAt()).isEqualTo(changedAt);
        assertThat(changed.role()).isEqualTo(account.role());
    }

    @Test
    void changePasswordDoesNotResetLockoutState() {
        EmployeeAccount locked = lockedAtLevelOne();

        EmployeeAccount changed = locked.changePassword("new-argon2-hash", locked.lockedUntil().plusSeconds(1));

        assertThat(changed.failedLoginCount()).isEqualTo(locked.failedLoginCount());
        assertThat(changed.lockoutLevel()).isEqualTo(locked.lockoutLevel());
        assertThat(changed.lockedUntil()).isEqualTo(locked.lockedUntil());
    }

    @Test
    void resetPasswordSetsANewTwentyFourHourTemporaryPasswordAndDoesNotTouchLastAuthenticatedAt() {
        EmployeeAccount account = activeAccount().changePassword("old-argon2-hash", now.plusSeconds(10))
                .recordSuccessfulLogin(now.plusSeconds(20));
        Instant resetAt = now.plusSeconds(30);

        EmployeeAccount reset = account.resetPassword("temp-argon2-hash", resetAt);

        assertThat(reset.passwordHash()).isEqualTo("temp-argon2-hash");
        assertThat(reset.passwordChangeRequired()).isTrue();
        assertThat(reset.temporaryPasswordExpiresAt()).isEqualTo(resetAt.plus(Duration.ofHours(24)));
        assertThat(reset.lastPasswordAuthenticatedAt()).isEqualTo(account.lastPasswordAuthenticatedAt());
        assertThat(reset.updatedAt()).isEqualTo(resetAt);
    }

    private EmployeeAccount activeAccount() {
        return EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new LoginId("owner01"), "argon2-hash", Role.OWNER, now);
    }

    private EmployeeAccount lockedAtLevelOne() {
        EmployeeAccount account = activeAccount();
        for (int i = 1; i <= 5; i++) {
            account = account.recordFailedLogin(now.plusSeconds(i));
        }
        return account;
    }
}
