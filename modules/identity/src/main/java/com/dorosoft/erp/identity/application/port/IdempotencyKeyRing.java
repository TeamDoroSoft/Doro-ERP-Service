package com.dorosoft.erp.identity.application.port;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pod 시작 시 주입된 활성·직전 Identity 멱등 Master Key. */
public final class IdempotencyKeyRing {
    private final KeyMaterial active;
    private final KeyMaterial previous;

    public IdempotencyKeyRing(
            String activeVersion,
            byte[] activeKey,
            String previousVersion,
            byte[] previousKey
    ) {
        this.active = new KeyMaterial(activeVersion, activeKey);
        boolean previousVersionPresent = previousVersion != null && !previousVersion.isBlank();
        boolean previousKeyPresent = previousKey != null;
        if (previousVersionPresent != previousKeyPresent) {
            throw new IllegalArgumentException("previous idempotency version and key must be configured together");
        }
        this.previous = previousVersionPresent ? new KeyMaterial(previousVersion, previousKey) : null;
        if (previous != null) {
            if (active.version().equals(previous.version())) {
                throw new IllegalArgumentException("active and previous idempotency versions must differ");
            }
            if (java.security.MessageDigest.isEqual(active.key(), previous.key())) {
                throw new IllegalArgumentException("active and previous idempotency keys must differ");
            }
        }
    }

    public KeyMaterial active() {
        return active.copy();
    }

    public Optional<KeyMaterial> previous() {
        return Optional.ofNullable(previous == null ? null : previous.copy());
    }

    public List<KeyMaterial> readCandidates() {
        return previous == null ? List.of(active()) : List.of(active(), previous.copy());
    }

    public KeyMaterial requireVersion(String version) {
        Objects.requireNonNull(version, "version");
        if (active.version().equals(version)) {
            return active();
        }
        if (previous != null && previous.version().equals(version)) {
            return previous.copy();
        }
        throw new IllegalArgumentException("idempotency master key version is unavailable");
    }

    public record KeyMaterial(String version, byte[] key) {
        public KeyMaterial {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(key, "key");
            if (version.isBlank() || version.length() > 50) {
                throw new IllegalArgumentException("idempotency key version must contain 1 to 50 characters");
            }
            if (key.length < 32) {
                throw new IllegalArgumentException("idempotency master key must contain at least 32 bytes");
            }
            key = Arrays.copyOf(key, key.length);
        }

        @Override
        public byte[] key() {
            return Arrays.copyOf(key, key.length);
        }

        KeyMaterial copy() {
            return new KeyMaterial(version, key);
        }
    }
}
