package com.dorosoft.erp.config;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertiesValidationTest {

    private static final String RATE_LIMIT_KEY = key((byte) 1);
    private static final String IDEMPOTENCY_KEY = key((byte) 2);
    private static final String AUDIT_PAYLOAD_KEY = key((byte) 3);
    private static final String AUDIT_CURSOR_KEY = key((byte) 4);
    private static final String PRIVACY_ADDRESS_KEY = key((byte) 5);
    private static final String RATE_LIMIT_PREVIOUS_KEY = key((byte) 6);
    private static final String IDEMPOTENCY_PREVIOUS_KEY = key((byte) 7);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ValidationTestConfiguration.class)
            .withPropertyValues(validProductionProperties());

    @Test
    void shouldPassWhenRequiredProductionValuesAreValid() {
        contextRunner.run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void shouldFailWhenProductionPublicUrlIsNotHttps() {
        contextRunner
                .withPropertyValues("doro.erp.public-base-url=http://api.example.com")
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "doro.erp.public-base-url must use https in production"));
    }

    @Test
    void shouldFailWhenProductionRedisDoesNotUseTls() {
        contextRunner
                .withPropertyValues("spring.data.redis.ssl.enabled=false")
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "spring.data.redis.ssl.enabled must be true in production"));
    }

    @Test
    void shouldFailWhenProductionUsesLocalDatasource() {
        contextRunner
                .withPropertyValues("spring.datasource.url=jdbc:mysql://localhost:3306/doro_erp")
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "spring.datasource.url must not use a local host in production"));
    }

    @Test
    void shouldFailWhenRedisNamespaceDoesNotMatchEnvironmentAndTenant() {
        contextRunner
                .withPropertyValues("spring.session.data.redis.namespace=erp:prod:other-store:identity")
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "spring.session.data.redis.namespace must match"));
    }

    @Test
    void shouldFailWhenIdentityKeyIsNotValidBase64() {
        contextRunner
                .withPropertyValues("identity.rate-limit.hmac-key-base64=not-base64")
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "identity.rate-limit.hmac-key-base64 must be valid Base64"));
    }

    @Test
    void shouldFailWhenIdentityKeyIsShorterThanThirtyTwoBytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);

        contextRunner
                .withPropertyValues("identity.idempotency.master-key-base64=" + shortKey)
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "identity.idempotency.master-key-base64 must decode to at least 32 bytes"));
    }

    @Test
    void shouldFailWhenIdempotencyPreviousVersionAndKeyAreNotPaired() {
        contextRunner
                .withPropertyValues("identity.idempotency.previous-master-key-version=v0")
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "identity.idempotency previous version and key must be set together"));
    }

    @Test
    void shouldFailWhenRateLimitActiveAndPreviousKeysAreEqual() {
        contextRunner
                .withPropertyValues("identity.rate-limit.hmac-previous-key-base64=" + RATE_LIMIT_KEY)
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "identity.rate-limit active and previous keys must not be equal"));
    }

    @Test
    void shouldFailWhenIdentityPurposesReuseTheSameActiveKey() {
        contextRunner
                .withPropertyValues("identity.idempotency.master-key-base64=" + RATE_LIMIT_KEY)
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "identity rate-limit and idempotency active keys must not be equal"));
    }

    @Test
    void shouldPassWithDistinctPreviousIdentityKeys() {
        contextRunner
                .withPropertyValues(
                        "identity.rate-limit.hmac-previous-key-base64=" + RATE_LIMIT_PREVIOUS_KEY,
                        "identity.idempotency.previous-master-key-version=v0",
                        "identity.idempotency.previous-master-key-base64=" + IDEMPOTENCY_PREVIOUS_KEY
                )
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void shouldFailWhenAuditPayloadKeyIsMissingInProduction() {
        contextRunner
                .withPropertyValues("audit.security.payload-hmac.key-base64=")
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "audit.security.payload-hmac.key-base64 must be set in production"));
    }

    @Test
    void shouldFailWhenPrivacyAddressKeyIsNotExactlyThirtyTwoBytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);
        contextRunner
                .withPropertyValues("audit.security.privacy-client-address.key-base64=" + shortKey)
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "privacy-client-address.key-base64 must decode to exactly 32 bytes"));
    }

    @Test
    void shouldFailWhenAuditAndIdentityReuseAnActiveKey() {
        contextRunner
                .withPropertyValues("audit.security.cursor-hmac.key-base64=" + RATE_LIMIT_KEY)
                .run(context -> assertFailureContains(context.getStartupFailure(),
                        "audit cursor and identity rate limit keys must not be equal"));
    }

    private static String[] validProductionProperties() {
        return new String[] {
                "spring.profiles.active=prod",
                "doro.erp.environment=prod",
                "doro.erp.tenant-id=store-a",
                "doro.erp.public-base-url=https://api.store-a.example.com",
                "doro.erp.allowed-origins=https://store-a.example.com",
                "spring.datasource.url=jdbc:mysql://mysql.store-a.internal:3306/doro_erp",
                "spring.datasource.username=erp_api",
                "spring.datasource.password=test-only-password",
                "spring.data.redis.host=redis.store-a.internal",
                "spring.data.redis.port=6379",
                "spring.data.redis.username=erp_api",
                "spring.data.redis.password=test-only-password",
                "spring.data.redis.ssl.enabled=true",
                "spring.session.data.redis.namespace=erp:prod:store-a:identity",
                "identity.rate-limit.hmac-key-base64=" + RATE_LIMIT_KEY,
                "identity.idempotency.master-key-version=v1",
                "identity.idempotency.master-key-base64=" + IDEMPOTENCY_KEY,
                "audit.security.payload-hmac.key-base64=" + AUDIT_PAYLOAD_KEY,
                "audit.security.cursor-hmac.key-base64=" + AUDIT_CURSOR_KEY,
                "audit.security.privacy-client-address.key-version=v1",
                "audit.security.privacy-client-address.key-base64=" + PRIVACY_ADDRESS_KEY
        };
    }

    private static String key(byte value) {
        return Base64.getEncoder().encodeToString(filledBytes(value));
    }

    private static byte[] filledBytes(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    private static void assertFailureContains(Throwable startupFailure, String expectedText) {
        assertThat(startupFailure)
                .isNotNull()
                .hasStackTraceContaining(expectedText);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            DoroErpProperties.class,
            IdentitySecurityProperties.class,
            AuditSecurityProperties.class,
            DatasourceRuntimeProperties.class,
            RedisRuntimeProperties.class,
            RedisSessionProperties.class
    })
    @Import({IdentitySecurityPropertiesValidator.class, AuditSecurityPropertiesValidator.class})
    static class ValidationTestConfiguration {
    }
}
