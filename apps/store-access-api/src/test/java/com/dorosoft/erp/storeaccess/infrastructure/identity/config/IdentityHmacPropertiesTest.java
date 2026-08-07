package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Verifies the fail-closed startup contract for purpose-scoped HMAC secrets (ADR-02-007/013/014). */
class IdentityHmacPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void failsToStartWhenNoSecretIsConfigured() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWhenOnlySomeSecretsAreConfigured() {
        contextRunner
                .withPropertyValues("doro.identity.hmac.rate-limit-secret=rate-limit-secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsWhenAllPurposeSecretsAreConfigured() {
        contextRunner
                .withPropertyValues(
                        "doro.identity.hmac.rate-limit-secret=rate-limit-secret",
                        "doro.identity.hmac.idempotency-secret=idempotency-secret",
                        "doro.identity.hmac.kiosk-credential-secret=kiosk-credential-secret")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @EnableConfigurationProperties(IdentityHmacProperties.class)
    static class TestConfig {
    }
}
