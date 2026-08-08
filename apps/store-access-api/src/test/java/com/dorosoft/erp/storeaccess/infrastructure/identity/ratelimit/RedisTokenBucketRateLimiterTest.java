package com.dorosoft.erp.storeaccess.infrastructure.identity.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Verifies the Redis token bucket enforces capacity/refill and fails closed when Redis is unreachable. */
@Testcontainers
class RedisTokenBucketRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    private StringRedisTemplate redisTemplate;

    @BeforeAll
    static void startConnectionFactory() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopConnectionFactory() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @Test
    void allowsRequestsUpToCapacityThenDenies() {
        RedisTokenBucketRateLimiter limiter =
                new RedisTokenBucketRateLimiter(redisTemplate, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        String key = "test:" + UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume(key, 5, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)).allowed())
                    .as("attempt %d", i + 1)
                    .isTrue();
        }

        assertThat(limiter.tryConsume(key, 5, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)).allowed()).isFalse();
    }

    @Test
    void refillsTokensAfterTheRefillIntervalElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(redisTemplate, clock);
        String key = "test:" + UUID.randomUUID();

        assertThat(limiter.tryConsume(key, 1, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)).allowed()).isTrue();
        assertThat(limiter.tryConsume(key, 1, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryConsume(key, 1, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)).allowed()).isTrue();
    }

    @Test
    void distinctKeysHaveIndependentBuckets() {
        RedisTokenBucketRateLimiter limiter =
                new RedisTokenBucketRateLimiter(redisTemplate, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        String keyA = "test:" + UUID.randomUUID();
        String keyB = "test:" + UUID.randomUUID();

        assertThat(limiter.tryConsume(keyA, 1, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)).allowed()).isTrue();
        assertThat(limiter.tryConsume(keyA, 1, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)).allowed()).isFalse();

        assertThat(limiter.tryConsume(keyB, 1, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)).allowed()).isTrue();
    }

    @Test
    void propagatesFailureInsteadOfAllowingWhenRedisIsUnreachable() {
        LettuceClientConfiguration shortTimeoutConfig = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build())
                        .build())
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        LettuceConnectionFactory unreachableFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 1), shortTimeoutConfig);
        unreachableFactory.afterPropertiesSet();
        StringRedisTemplate unreachableTemplate = new StringRedisTemplate(unreachableFactory);
        unreachableTemplate.afterPropertiesSet();
        RedisTokenBucketRateLimiter limiter =
                new RedisTokenBucketRateLimiter(unreachableTemplate, Clock.systemUTC());

        try {
            assertThatThrownBy(() -> limiter.tryConsume("key", 5, 1, Duration.ofMinutes(1), Duration.ofMinutes(15)))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            unreachableFactory.destroy();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
