package com.dorosoft.erp.identity.infrastructure.ratelimit;

import com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitDecision;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitPort;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisLoginRateLimitAdapter implements LoginRateLimitPort {

    static final int LOGIN_CAPACITY = 10;
    static final long LOGIN_REFILL_MILLIS = 30_000;
    static final int IP_CAPACITY = 30;
    static final long IP_REFILL_MILLIS = 2_000;
    static final int TOKEN_SCALE = 1_000;
    static final long MARKER_TTL_MILLIS = 330_000;

    private final ScriptExecutor scriptExecutor;
    private final RateLimitKeyRing keyRing;
    private final String prefix;
    private final Clock clock;

    public RedisLoginRateLimitAdapter(
            StringRedisTemplate redisTemplate,
            RateLimitKeyRing keyRing,
            IdentityRedisNamespace namespace,
            Clock clock
    ) {
        this(new TemplateScriptExecutor(redisTemplate), keyRing, namespace, clock);
    }

    RedisLoginRateLimitAdapter(
            ScriptExecutor scriptExecutor,
            RateLimitKeyRing keyRing,
            IdentityRedisNamespace namespace,
            Clock clock
    ) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor must not be null");
        this.keyRing = Objects.requireNonNull(keyRing, "keyRing must not be null");
        this.prefix = Objects.requireNonNull(namespace, "namespace must not be null").rateLimitPrefix();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public LoginRateLimitDecision evaluate(String normalizedLoginId, ClientIpAddress clientIpAddress) {
        KeySet keys = keys(normalizedLoginId, clientIpAddress);
        try {
            List<?> result = scriptExecutor.execute(keys.redisKeys(), evalArguments());
            boolean allowed = number(result, 0) == 1;
            if (allowed) {
                return LoginRateLimitDecision.allow();
            }
            long retryAfterSeconds = Math.max(1, number(result, 1));
            boolean securityEventRequired = number(result, 2) == 1;
            String windowSeed = text(result, 3);
            return LoginRateLimitDecision.reject(Duration.ofSeconds(retryAfterSeconds),
                    securityEventRequired, deterministicWindowId(keys.markerKey(), windowSeed));
        } catch (AuthenticationUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthenticationUnavailableException(exception);
        }
    }

    @Override
    public void markSecurityEventRecorded(
            String normalizedLoginId,
            ClientIpAddress clientIpAddress,
            UUID limitedWindowId
    ) {
        Objects.requireNonNull(limitedWindowId, "limitedWindowId must not be null");
        KeySet keys = keys(normalizedLoginId, clientIpAddress);
        try {
            List<?> current = scriptExecutor.execute(keys.redisKeys(), inspectArguments());
            String windowSeed = text(current, 3);
            if (number(current, 0) != 1
                    || !limitedWindowId.equals(deterministicWindowId(keys.markerKey(), windowSeed))) {
                throw new AuthenticationUnavailableException();
            }
            List<?> acknowledged = scriptExecutor.execute(keys.redisKeys(), acknowledgeArguments(windowSeed));
            if (number(acknowledged, 0) != 1) {
                throw new AuthenticationUnavailableException();
            }
        } catch (AuthenticationUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthenticationUnavailableException(exception);
        }
    }

    private KeySet keys(String loginId, ClientIpAddress clientIpAddress) {
        String canonicalLoginId = normalizeLoginId(loginId);
        Objects.requireNonNull(clientIpAddress, "clientIpAddress must not be null");

        RateLimitKeyRing.KeyGeneration active = keyRing.active();
        String activeLoginDigest = digest(active, "login-id", canonicalLoginId.getBytes(StandardCharsets.UTF_8));
        String activeIpDigest = digest(active, "client-ip", clientIpAddress.bytes());
        List<String> keys = new ArrayList<>(5);
        keys.add(bucketKey(active, "login", activeLoginDigest));
        keys.add(bucketKey(active, "ip", activeIpDigest));

        RateLimitKeyRing.KeyGeneration canonical = active;
        String pairDigest;
        if (keyRing.previous().isPresent()) {
            RateLimitKeyRing.KeyGeneration previous = keyRing.previous().orElseThrow();
            keys.add(bucketKey(previous, "login",
                    digest(previous, "login-id", canonicalLoginId.getBytes(StandardCharsets.UTF_8))));
            keys.add(bucketKey(previous, "ip", digest(previous, "client-ip", clientIpAddress.bytes())));
            canonical = previous;
        } else {
            keys.add(prefix + ":unused:login");
            keys.add(prefix + ":unused:ip");
        }
        pairDigest = digest(canonical, "limited-pair", pairBytes(canonicalLoginId, clientIpAddress));
        String markerKey = prefix + ":marker:" + canonical.id() + ":" + pairDigest;
        keys.add(markerKey);
        return new KeySet(List.copyOf(keys), markerKey);
    }

    private List<String> evalArguments() {
        return arguments("EVAL", "");
    }

    private List<String> inspectArguments() {
        return arguments("INSPECT", "");
    }

    private List<String> acknowledgeArguments(String windowSeed) {
        return arguments("ACK", windowSeed);
    }

    private List<String> arguments(String mode, String windowSeed) {
        return List.of(
                mode,
                Long.toString(clock.millis()),
                keyRing.previous().isPresent() ? "1" : "0",
                Integer.toString(LOGIN_CAPACITY),
                Long.toString(LOGIN_REFILL_MILLIS),
                Integer.toString(IP_CAPACITY),
                Long.toString(IP_REFILL_MILLIS),
                Integer.toString(TOKEN_SCALE),
                Long.toString(MARKER_TTL_MILLIS),
                windowSeed
        );
    }

    private String bucketKey(RateLimitKeyRing.KeyGeneration generation, String kind, String digest) {
        return prefix + ":bucket:" + generation.id() + ":" + kind + ":" + digest;
    }

    private String normalizeLoginId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("normalizedLoginId must not be null");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("normalizedLoginId must not be blank");
        }
        return normalized;
    }

    private byte[] pairBytes(String loginId, ClientIpAddress clientIpAddress) {
        byte[] login = loginId.getBytes(StandardCharsets.UTF_8);
        byte[] ip = clientIpAddress.bytes();
        return ByteBuffer.allocate(login.length + 1 + ip.length).put(login).put((byte) 0).put(ip).array();
    }

    private String digest(RateLimitKeyRing.KeyGeneration generation, String purpose, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(generation.key(), "HmacSHA256"));
            mac.update(("doro-erp/identity/rate-limit/" + purpose).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }

    private UUID deterministicWindowId(String markerKey, String windowSeed) {
        if (windowSeed == null || windowSeed.isBlank()) {
            throw new AuthenticationUnavailableException();
        }
        return UUID.nameUUIDFromBytes((markerKey + ':' + windowSeed).getBytes(StandardCharsets.UTF_8));
    }

    private long number(List<?> result, int index) {
        if (result == null || result.size() <= index) {
            throw new AuthenticationUnavailableException();
        }
        Object value = result.get(index);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(List<?> result, int index) {
        if (result == null || result.size() <= index || result.get(index) == null) {
            throw new AuthenticationUnavailableException();
        }
        Object value = result.get(index);
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value);
    }

    record KeySet(List<String> redisKeys, String markerKey) {
    }

    @FunctionalInterface
    interface ScriptExecutor {
        List<?> execute(List<String> keys, List<String> arguments);
    }

    private static final class TemplateScriptExecutor implements ScriptExecutor {

        @SuppressWarnings("rawtypes")
        private final DefaultRedisScript<List> script;
        private final StringRedisTemplate redisTemplate;

        private TemplateScriptExecutor(StringRedisTemplate redisTemplate) {
            this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
            this.script = new DefaultRedisScript<>();
            this.script.setLocation(new ClassPathResource(
                    "com/dorosoft/erp/identity/ratelimit/identity-login-rate-limit.lua"));
            this.script.setResultType(List.class);
        }

        @Override
        public List<?> execute(List<String> keys, List<String> arguments) {
            return redisTemplate.execute(script, keys, arguments.toArray());
        }
    }
}
