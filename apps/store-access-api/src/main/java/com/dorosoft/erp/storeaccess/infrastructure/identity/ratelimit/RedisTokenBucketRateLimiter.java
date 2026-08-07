package com.dorosoft.erp.storeaccess.infrastructure.identity.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Atomic Redis token bucket rate limiter (ADR-02-007). Callers are responsible for the fail-closed
 * decision: this component never swallows a Redis failure and default to "allowed" — it propagates the
 * underlying {@code DataAccessException} so the caller can reject the request instead.
 */
@Component
public class RedisTokenBucketRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;
    private final Clock clock;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.script = RedisScript.of(new ClassPathResource("scripts/identity-token-bucket.lua"), Long.class);
    }

    public RateLimitDecision tryConsume(
            String key, int capacity, int refillTokens, Duration refillInterval, Duration bucketTtl) {
        Long allowed = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(capacity),
                String.valueOf(refillTokens),
                String.valueOf(refillInterval.toMillis()),
                String.valueOf(clock.millis()),
                String.valueOf(bucketTtl.toSeconds()));
        return new RateLimitDecision(allowed != null && allowed == 1L);
    }
}
