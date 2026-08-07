package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityInfrastructureConfig;
import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityPasswordProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class DummyPasswordHashTest {

    private final PasswordEncoder passwordEncoder = new IdentityInfrastructureConfig()
            .passwordEncoder(new IdentityPasswordProperties(19456, 2, 1, 16, 32));

    @Test
    void producesAValidArgon2idHash() {
        DummyPasswordHash dummyPasswordHash = new DummyPasswordHash(passwordEncoder);

        assertThat(dummyPasswordHash.value()).startsWith("$argon2id$");
    }

    @Test
    void isGeneratedOnceAndReusedAcrossCalls() {
        DummyPasswordHash dummyPasswordHash = new DummyPasswordHash(passwordEncoder);

        assertThat(dummyPasswordHash.value()).isEqualTo(dummyPasswordHash.value());
    }

    @Test
    void separateInstancesGenerateDifferentHashesSinceEachIsARandomDummyPassword() {
        DummyPasswordHash first = new DummyPasswordHash(passwordEncoder);
        DummyPasswordHash second = new DummyPasswordHash(passwordEncoder);

        assertThat(first.value()).isNotEqualTo(second.value());
    }
}
