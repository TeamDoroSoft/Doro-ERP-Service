package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacDigesterTest {

    private final HmacDigester digester = new HmacDigester();

    @Test
    void sameSecretAndInputProduceTheSameDigest() {
        String first = digester.digest("secret", "tenant-a:owner01");
        String second = digester.digest("secret", "tenant-a:owner01");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentSecretsProduceDifferentDigests() {
        String withFirstSecret = digester.digest("secret-one", "same-input");
        String withSecondSecret = digester.digest("secret-two", "same-input");

        assertThat(withFirstSecret).isNotEqualTo(withSecondSecret);
    }

    @Test
    void differentInputsProduceDifferentDigests() {
        String first = digester.digest("secret", "input-one");
        String second = digester.digest("secret", "input-two");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void digestIsUrlSafeBase64WithoutPadding() {
        String digest = digester.digest("secret", "input");

        assertThat(digest).doesNotContain("+", "/", "=");
    }
}
