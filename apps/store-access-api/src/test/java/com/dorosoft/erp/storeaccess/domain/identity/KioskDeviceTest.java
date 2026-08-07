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

    @Test
    void rotateReplacesCredentialAndIncrementsVersionWithoutChangingStatus() {
        KioskDevice device = KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POS-01", "cred-abc123", "digest-1", now);
        Instant rotatedAt = now.plusSeconds(60);

        KioskDevice rotated = device.rotate("cred-xyz789", "digest-2", rotatedAt);

        assertThat(rotated.credentialId()).isEqualTo("cred-xyz789");
        assertThat(rotated.secretDigest()).isEqualTo("digest-2");
        assertThat(rotated.credentialVersion()).isEqualTo(2);
        assertThat(rotated.status()).isEqualTo(KioskDeviceStatus.ACTIVE);
        assertThat(rotated.updatedAt()).isEqualTo(rotatedAt);
        assertThat(rotated.createdAt()).isEqualTo(device.createdAt());
    }

    @Test
    void revokeSetsStatusRevokedAndIncrementsVersionWithoutChangingCredential() {
        KioskDevice device = KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POS-01", "cred-abc123", "digest-1", now);
        Instant revokedAt = now.plusSeconds(60);

        KioskDevice revoked = device.revoke(revokedAt);

        assertThat(revoked.status()).isEqualTo(KioskDeviceStatus.REVOKED);
        assertThat(revoked.credentialVersion()).isEqualTo(2);
        assertThat(revoked.credentialId()).isEqualTo(device.credentialId());
        assertThat(revoked.secretDigest()).isEqualTo(device.secretDigest());
        assertThat(revoked.updatedAt()).isEqualTo(revokedAt);
    }
}
