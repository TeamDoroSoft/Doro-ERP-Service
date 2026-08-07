package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentityInfrastructureConfigTest {

    private final IdentityInfrastructureConfig config = new IdentityInfrastructureConfig();

    @Test
    void clockUsesUtc() {
        Clock clock = config.clock();

        assertThat(clock.getZone().getId()).isEqualTo("Z");
    }

    @Test
    void passwordEncoderHashesAndMatchesUsingArgon2Parameters() {
        PasswordEncoder encoder = config.passwordEncoder(new IdentityPasswordProperties(19456, 2, 1, 16, 32));

        String encoded = encoder.encode("a-temporary-password-123");

        assertThat(encoded).startsWith("$argon2id$");
        assertThat(encoder.matches("a-temporary-password-123", encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void passwordEncoderProducesDifferentHashesForTheSamePasswordDueToRandomSalt() {
        PasswordEncoder encoder = config.passwordEncoder(new IdentityPasswordProperties(19456, 2, 1, 16, 32));

        String first = encoder.encode("a-temporary-password-123");
        String second = encoder.encode("a-temporary-password-123");

        assertThat(first).isNotEqualTo(second);
    }
}
