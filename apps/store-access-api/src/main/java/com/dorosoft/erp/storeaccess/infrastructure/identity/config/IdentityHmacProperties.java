package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Purpose-scoped HMAC pepper secrets. Each purpose uses an independent secret so that a compromise of one
 * scope (e.g. rate limit keys) does not expose another (e.g. idempotency digests). No safe default is
 * provided: a missing value fails validation and the application fails closed at startup.
 */
@ConfigurationProperties(prefix = "doro.identity.hmac")
@Validated
public record IdentityHmacProperties(
        @NotBlank String rateLimitSecret,
        @NotBlank String idempotencySecret,
        @NotBlank String kioskCredentialSecret) {
}
