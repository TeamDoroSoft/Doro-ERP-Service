package com.dorosoft.erp.storeaccess.infrastructure.identity.ratelimit;

import com.dorosoft.erp.storeaccess.application.port.identity.LoginRateLimiter;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityRateLimitProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class RedisLoginRateLimiterAdapter implements LoginRateLimiter {

    private static final Duration BUCKET_TTL = Duration.ofMinutes(15);

    private final RedisTokenBucketRateLimiter rateLimiter;
    private final LoginRateLimitKeyFactory keyFactory;
    private final IdentityRateLimitProperties properties;

    RedisLoginRateLimiterAdapter(
            RedisTokenBucketRateLimiter rateLimiter,
            LoginRateLimitKeyFactory keyFactory,
            IdentityRateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.keyFactory = keyFactory;
        this.properties = properties;
    }

    @Override
    public boolean tryConsumeAccountScope(String normalizedTenantCode, LoginId loginId) {
        IdentityRateLimitProperties.BucketSettings settings = properties.account();
        return rateLimiter
                .tryConsume(
                        keyFactory.accountKey(normalizedTenantCode, loginId),
                        settings.capacity(),
                        settings.refillTokens(),
                        settings.refillInterval(),
                        BUCKET_TTL)
                .allowed();
    }

    @Override
    public boolean tryConsumeClientIpScope(String clientIp) {
        IdentityRateLimitProperties.BucketSettings settings = properties.clientIp();
        return rateLimiter
                .tryConsume(
                        keyFactory.clientIpKey(clientIp),
                        settings.capacity(),
                        settings.refillTokens(),
                        settings.refillInterval(),
                        BUCKET_TTL)
                .allowed();
    }
}
