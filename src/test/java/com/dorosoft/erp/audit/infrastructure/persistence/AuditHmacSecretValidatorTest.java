package com.dorosoft.erp.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuditHmacSecretValidatorTest {

    @Test
    void prodProfileRejectsDefaultHmacSecret() {
        AuditHmacSecretValidator validator =
                validatorWith("prod", AuditHmacSecretValidator.DEFAULT_HMAC_SECRET);

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodProfileAcceptsConfiguredHmacSecret() {
        AuditHmacSecretValidator validator =
                validatorWith("prod", "configured-production-secret");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void nonProdProfileAcceptsDefaultHmacSecret() {
        AuditHmacSecretValidator validator =
                validatorWith("test", AuditHmacSecretValidator.DEFAULT_HMAC_SECRET);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private static AuditHmacSecretValidator validatorWith(String profile, String hmacSecret) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        AuditProperties properties = new AuditProperties();
        properties.setHmacSecret(hmacSecret);
        return new AuditHmacSecretValidator(properties, environment);
    }
}
