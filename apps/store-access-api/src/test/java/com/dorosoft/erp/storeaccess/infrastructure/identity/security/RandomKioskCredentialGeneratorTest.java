package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.storeaccess.application.port.identity.GeneratedKioskCredential;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RandomKioskCredentialGeneratorTest {

    private final RandomKioskCredentialGenerator generator = new RandomKioskCredentialGenerator();

    @Test
    void fullCredentialFollowsThePrefixedDotSeparatedFormat() {
        GeneratedKioskCredential credential = generator.generate();

        assertThat(credential.fullCredential())
                .isEqualTo("kdc_" + credential.credentialId() + "." + credential.secret());
    }

    @Test
    void credentialIdAndSecretAreUrlSafeBase64WithoutPadding() {
        GeneratedKioskCredential credential = generator.generate();

        assertThat(credential.credentialId()).doesNotContain("+", "/", "=");
        assertThat(credential.secret()).doesNotContain("+", "/", "=");
    }

    @Test
    void secretDecodesToThirtyTwoBytesForTwoHundredFiftySixBitsOfEntropy() {
        GeneratedKioskCredential credential = generator.generate();

        byte[] decoded = Base64.getUrlDecoder().decode(credential.secret());

        assertThat(decoded).hasSize(32);
    }

    @Test
    void eachCallGeneratesADistinctCredentialIdAndSecret() {
        GeneratedKioskCredential first = generator.generate();
        GeneratedKioskCredential second = generator.generate();

        assertThat(first.credentialId()).isNotEqualTo(second.credentialId());
        assertThat(first.secret()).isNotEqualTo(second.secret());
    }
}
