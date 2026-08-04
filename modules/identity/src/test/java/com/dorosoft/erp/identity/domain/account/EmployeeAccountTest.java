package com.dorosoft.erp.identity.domain.account;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeAccountTest {

    @Test
    void fiveConsecutiveFailuresMoveToTemporaryLock() {
        EmployeeAccount account = EmployeeAccount.bootstrapActiveAccount(
                UUID.randomUUID(),
                "staff01",
                "test"
        );
        Instant now = Instant.parse("2026-08-04T00:00:00Z");

        EmployeeAccount afterFailures = account;
        for (int i = 0; i < 5; i++) {
            afterFailures = afterFailures.withFailedLoginAttempt(now.plusSeconds(i));
        }

        assertEquals(LoginLockStatus.TEMPORARY, afterFailures.loginLockStatus());
        assertEquals(1, afterFailures.temporaryLockCount());
        assertEquals(5, afterFailures.failedLoginCount());
        assertEquals(now.plusSeconds(4).plus(5, ChronoUnit.MINUTES), afterFailures.lockedUntil());
        assertTrue(afterFailures.isLocked(now.plusSeconds(10)));
    }

    @Test
    void expiredTemporaryLockResetsFailCountAndKeepsTemporaryHistory() {
        EmployeeAccount account = EmployeeAccount.bootstrapActiveAccount(
                UUID.randomUUID(),
                "staff01",
                "test"
        );
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        EmployeeAccount temporary = account;
        for (int i = 0; i < 5; i++) {
            temporary = temporary.withFailedLoginAttempt(now.plusSeconds(i));
        }

        EmployeeAccount afterExpiry = temporary.clearTemporaryIfExpired(now.plus(6, ChronoUnit.MINUTES));
        assertEquals(LoginLockStatus.NONE, afterExpiry.loginLockStatus());
        assertEquals(0, afterExpiry.failedLoginCount());
        assertEquals(1, afterExpiry.temporaryLockCount());
        assertEquals(null, afterExpiry.lockedUntil());
    }

    @Test
    void anotherFiveConsecutiveFailuresAfterTemporaryLockMovesToPermanentLock() {
        EmployeeAccount account = EmployeeAccount.bootstrapActiveAccount(
                UUID.randomUUID(),
                "staff01",
                "test"
        );
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        EmployeeAccount temporary = account;
        for (int i = 0; i < 5; i++) {
            temporary = temporary.withFailedLoginAttempt(now.plusSeconds(i));
        }
        EmployeeAccount permanent = temporary
                .withFailedLoginAttempt(now.plus(6, ChronoUnit.MINUTES))
                .withFailedLoginAttempt(now.plus(6, ChronoUnit.MINUTES).plusSeconds(1))
                .withFailedLoginAttempt(now.plus(6, ChronoUnit.MINUTES).plusSeconds(2))
                .withFailedLoginAttempt(now.plus(6, ChronoUnit.MINUTES).plusSeconds(3))
                .withFailedLoginAttempt(now.plus(6, ChronoUnit.MINUTES).plusSeconds(4));

        assertEquals(LoginLockStatus.PERMANENT, permanent.loginLockStatus());
    }

    @Test
    void successfulLoginClearsFailureAndTemporaryLockCounter() {
        EmployeeAccount account = new EmployeeAccount(
                UUID.randomUUID(),
                "staff01",
                "test",
                AccountStatus.ACTIVE,
                LoginLockStatus.TEMPORARY,
                5,
                1,
                Instant.parse("2026-08-04T00:00:00Z"),
                Instant.parse("2026-08-04T00:05:00Z"),
                1L
        );

        EmployeeAccount afterClear = account.clearTemporaryIfExpired(Instant.parse("2026-08-04T00:05:01Z"));
        EmployeeAccount login = afterClear.withSuccessfulLogin();

        assertEquals(LoginLockStatus.NONE, login.loginLockStatus());
        assertEquals(0, login.failedLoginCount());
        assertEquals(0, login.temporaryLockCount());
        assertEquals(null, login.lockedAt());
        assertEquals(null, login.lockedUntil());
    }

    @Test
    void administratorUnlockPreservesInactiveStatusAndNoopDoesNotIncrementVersion() {
        EmployeeAccount inactiveLocked = new EmployeeAccount(
                UUID.randomUUID(), "staff01", "test", AccountStatus.INACTIVE,
                LoginLockStatus.PERMANENT, 5, 1,
                Instant.parse("2026-08-04T00:00:00Z"), null, 7L
        );

        EmployeeAccount unlocked = inactiveLocked.withAdministratorUnlock();
        assertEquals(AccountStatus.INACTIVE, unlocked.accountStatus());
        assertEquals(7L, unlocked.version());
        assertEquals(7L, unlocked.withAdministratorUnlock().version());
    }

    @Test
    void temporaryLockMustLastExactlyFiveMinutes() {
        assertThrows(IllegalArgumentException.class, () -> new EmployeeAccount(
                UUID.randomUUID(), "staff01", "test", AccountStatus.ACTIVE,
                LoginLockStatus.TEMPORARY, 5, 1,
                Instant.parse("2026-08-04T00:00:00Z"),
                Instant.parse("2026-08-04T00:04:59Z"), 1L
        ));
    }
}
