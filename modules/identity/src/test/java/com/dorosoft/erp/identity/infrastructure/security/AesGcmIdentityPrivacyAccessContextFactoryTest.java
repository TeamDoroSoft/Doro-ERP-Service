package com.dorosoft.erp.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AesGcmIdentityPrivacyAccessContextFactoryTest {

    @Test
    void encryptsTheCanonicalAddressAndNeverReturnsPlaintext() {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 7);
        SecureRandom random = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 3);
            }
        };
        var factory = new AesGcmIdentityPrivacyAccessContextFactory("privacy-v1", key, random);

        var context = factory.create(
                "tenant-a", UUID.randomUUID(), "ADMIN", "req-1",
                "2001:db8::1", Instant.parse("2026-08-04T00:00:00Z"));

        byte[] envelope = Base64.getDecoder().decode(context.clientAddressCiphertext());
        assertThat(envelope).hasSizeGreaterThan(12);
        assertThat(new String(envelope, StandardCharsets.ISO_8859_1)).doesNotContain("2001:db8");
        assertThat(context.clientAddressKeyVersion()).isEqualTo("privacy-v1");
        assertThat(context.accessorType()).isEqualTo("ADMIN");
    }

    @Test
    void rejectsKeysThatAreNotAes256() {
        assertThatThrownBy(() -> new AesGcmIdentityPrivacyAccessContextFactory("v1", new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
