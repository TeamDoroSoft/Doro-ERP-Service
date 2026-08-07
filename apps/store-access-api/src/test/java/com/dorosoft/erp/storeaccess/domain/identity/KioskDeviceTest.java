package com.dorosoft.erp.storeaccess.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KioskDeviceTest {

    private final Instant now = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void registerStartsActiveAtCredentialVersionOne() {
        KioskDevice device = KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POS-01", "cred-abc123", "digest", now);

        assertThat(device.status()).isEqualTo(KioskDeviceStatus.ACTIVE);
        assertThat(device.credentialVersion()).isEqualTo(1);
        assertThat(device.createdAt()).isEqualTo(now);
        assertThat(device.updatedAt()).isEqualTo(now);
    }

    @Test
    void rejectsBlankDeviceCode() {
        assertThatThrownBy(() -> KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), " ", "cred-abc123", "digest", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankCredentialId() {
        assertThatThrownBy(() -> KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POS-01", " ", "digest", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSecretDigest() {
        assertThatThrownBy(() -> KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POS-01", "cred-abc123", " ", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDeviceCodeLongerThanOneHundredCharacters() {
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), tooLong, "cred-abc123", "digest", now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
