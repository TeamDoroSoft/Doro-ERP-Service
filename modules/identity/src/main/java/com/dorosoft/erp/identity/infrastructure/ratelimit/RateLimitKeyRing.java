package com.dorosoft.erp.identity.infrastructure.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

public final class RateLimitKeyRing {

    private static final int MINIMUM_KEY_BYTES = 32;

    private final KeyGeneration active;
    private final KeyGeneration previous;

    public RateLimitKeyRing(byte[] activeKey, byte[] previousKey) {
        this.active = new KeyGeneration(requireKey(activeKey, "active"));
        this.previous = previousKey == null ? null : new KeyGeneration(requireKey(previousKey, "previous"));
        if (previous != null && MessageDigest.isEqual(active.key, previous.key)) {
            throw new IllegalArgumentException("Rate limit active and previous keys must differ");
        }
    }

    public static RateLimitKeyRing fromBase64(String activeBase64, String previousBase64) {
        return new RateLimitKeyRing(decode(activeBase64, "active"),
                previousBase64 == null || previousBase64.isBlank() ? null : decode(previousBase64, "previous"));
    }

    KeyGeneration active() {
        return active;
    }

    Optional<KeyGeneration> previous() {
        return Optional.ofNullable(previous);
    }

    private static byte[] decode(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Rate limit " + name + " key must be configured");
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Rate limit " + name + " key must be valid Base64", exception);
        }
    }

    private static byte[] requireKey(byte[] key, String name) {
        if (key == null || key.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("Rate limit " + name + " key must contain at least 32 bytes");
        }
        return key.clone();
    }

    static final class KeyGeneration {

        private final byte[] key;
        private final String id;

        private KeyGeneration(byte[] key) {
            this.key = key;
            this.id = generationId(key);
        }

        byte[] key() {
            return key.clone();
        }

        String id() {
            return id;
        }

        private static String generationId(byte[] key) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update("doro-erp/identity/rate-limit/generation".getBytes(StandardCharsets.UTF_8));
                return Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(Arrays.copyOf(digest.digest(key), 12));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }
}
