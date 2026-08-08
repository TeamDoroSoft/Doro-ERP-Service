package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Login rate limit bucket settings for the account and client-IP scopes (ADR-02-007). */
@ConfigurationProperties(prefix = "doro.identity.rate-limit")
public record IdentityRateLimitProperties(BucketSettings account, BucketSettings clientIp) {

    public IdentityRateLimitProperties {
        Objects.requireNonNull(account, "account rate limit settings must be configured");
        Objects.requireNonNull(clientIp, "clientIp rate limit settings must be configured");
    }

    public record BucketSettings(int capacity, int refillTokens, Duration refillInterval) {

        public BucketSettings {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            if (refillTokens <= 0) {
                throw new IllegalArgumentException("refillTokens must be positive");
            }
            if (refillInterval == null || refillInterval.isZero() || refillInterval.isNegative()) {
                throw new IllegalArgumentException("refillInterval must be positive");
            }
        }
    }
}
