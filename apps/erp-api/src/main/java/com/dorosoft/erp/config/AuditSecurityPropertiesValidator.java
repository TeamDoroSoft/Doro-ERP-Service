package com.dorosoft.erp.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Validates Feature 17 key presence, shape, rotation pairs and cross-purpose separation. */
@Component
public final class AuditSecurityPropertiesValidator {
    private static final int MINIMUM_HMAC_KEY_BYTES = 32;
    private static final int AES_256_KEY_BYTES = 32;

    private final AuditSecurityProperties audit;
    private final IdentitySecurityProperties identity;
    private final Environment environment;

    public AuditSecurityPropertiesValidator(
            AuditSecurityProperties audit,
            IdentitySecurityProperties identity,
            Environment environment
    ) {
        this.audit = audit;
        this.identity = identity;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        validateHmacRing("audit.security.payload-hmac", audit.getPayloadHmac(), production);
        validateHmacRing("audit.security.cursor-hmac", audit.getCursorHmac(), production);
        validatePrivacyKey(production);
        validatePurposeSeparation();
    }

    private void validateHmacRing(
            String property,
            AuditSecurityProperties.KeyRing ring,
            boolean required
    ) {
        byte[] active = decode(property + ".key-base64", ring.getKeyBase64(), required);
        byte[] previous = decode(property + ".previous-key-base64", ring.getPreviousKeyBase64(), false);
        require(active == null || active.length >= MINIMUM_HMAC_KEY_BYTES,
                property + ".key-base64 must decode to at least 32 bytes");
        require(previous == null || previous.length >= MINIMUM_HMAC_KEY_BYTES,
                property + ".previous-key-base64 must decode to at least 32 bytes");
        require(previous == null || active != null,
                property + " previous key requires an active key");
        require(previous == null || !Arrays.equals(active, previous),
                property + " active and previous keys must not be equal");
    }

    private void validatePrivacyKey(boolean required) {
        String version = audit.getPrivacyClientAddress().getKeyVersion();
        String key = audit.getPrivacyClientAddress().getKeyBase64();
        boolean versionPresent = StringUtils.hasText(version);
        boolean keyPresent = StringUtils.hasText(key);
        require(versionPresent == keyPresent,
                "audit.security.privacy-client-address version and key must be set together");
        if (required) {
            require(versionPresent,
                    "audit.security.privacy-client-address.key-version must be set in production");
        }
        if (versionPresent) {
            require(version.length() <= 50,
                    "audit.security.privacy-client-address.key-version must be 1 to 50 characters");
        }
        byte[] decoded = decode(
                "audit.security.privacy-client-address.key-base64", key, required);
        require(decoded == null || decoded.length == AES_256_KEY_BYTES,
                "audit.security.privacy-client-address.key-base64 must decode to exactly 32 bytes");
    }

    private void validatePurposeSeparation() {
        List<NamedKey> keys = new ArrayList<>();
        add(keys, "audit payload", audit.getPayloadHmac().getKeyBase64());
        add(keys, "audit cursor", audit.getCursorHmac().getKeyBase64());
        add(keys, "privacy client address", audit.getPrivacyClientAddress().getKeyBase64());
        add(keys, "identity rate limit", identity.getRateLimit().getHmacKeyBase64());
        add(keys, "identity idempotency", identity.getIdempotency().getMasterKeyBase64());
        for (int left = 0; left < keys.size(); left++) {
            for (int right = left + 1; right < keys.size(); right++) {
                require(!Arrays.equals(keys.get(left).value(), keys.get(right).value()),
                        keys.get(left).name() + " and " + keys.get(right).name()
                                + " keys must not be equal");
            }
        }
    }

    private void add(List<NamedKey> keys, String name, String value) {
        if (StringUtils.hasText(value)) {
            try {
                keys.add(new NamedKey(name, Base64.getDecoder().decode(value)));
            } catch (IllegalArgumentException ignored) {
                // The owning validator reports the property-specific Base64 failure.
            }
        }
    }

    private byte[] decode(String property, String value, boolean required) {
        if (!StringUtils.hasText(value)) {
            require(!required, property + " must be set in production");
            return null;
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(property + " must be valid Base64", exception);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record NamedKey(String name, byte[] value) {
    }
}
