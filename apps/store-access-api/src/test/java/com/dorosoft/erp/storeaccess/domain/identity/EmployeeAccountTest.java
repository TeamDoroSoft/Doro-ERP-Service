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
}
