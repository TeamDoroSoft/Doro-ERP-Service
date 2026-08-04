package com.dorosoft.erp.audit.infrastructure;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.application.port.AuditCursorCodec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Signed, tenant- and filter-bound keyset cursor for the Feature 17 Audit query. */
public final class HmacAuditCursorCodec implements AuditCursorCodec {
    private static final int VERSION = 1;
    private static final int MIN_KEY_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[][] keys;
    private final Clock clock;
    private final Duration timeToLive;
    private final ObjectMapper objectMapper;

    public HmacAuditCursorCodec(byte[] key, Clock clock, Duration timeToLive, ObjectMapper objectMapper) {
        this(key, null, clock, timeToLive, objectMapper);
    }

    public HmacAuditCursorCodec(
            byte[] activeKey,
            byte[] previousKey,
            Clock clock,
            Duration timeToLive,
            ObjectMapper objectMapper
    ) {
        if (activeKey == null || activeKey.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("Audit cursor key must contain at least 32 bytes");
        }
        if (previousKey != null && previousKey.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("Previous audit cursor key must contain at least 32 bytes");
        }
        if (previousKey != null && MessageDigest.isEqual(activeKey, previousKey)) {
            throw new IllegalArgumentException("Audit cursor active and previous keys must differ");
        }
        if (timeToLive == null || timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("Audit cursor lifetime must be positive");
        }
        this.keys = previousKey == null
                ? new byte[][] {Arrays.copyOf(activeKey, activeKey.length)}
                : new byte[][] {
                    Arrays.copyOf(activeKey, activeKey.length),
                    Arrays.copyOf(previousKey, previousKey.length)
                };
        this.clock = Objects.requireNonNull(clock);
        this.timeToLive = timeToLive;
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public String encode(
            String tenantId,
            AuditQueryFilter filter,
            Instant occurredAt,
            UUID auditId
    ) {
        requireContext(tenantId, filter);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(auditId);
        try {
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("v", VERSION);
            payload.put("tenant", tenantId);
            payload.put("filter", filterDigest(filter, keys[0]));
            payload.put("occurredAt", occurredAt.toString());
            payload.put("auditId", auditId.toString());
            payload.put("expiresAt", clock.instant().plus(timeToLive).getEpochSecond());
            String encoded = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            return encoded + "." + ENCODER.encodeToString(hmac(keys[0], "cursor:" + encoded));
        } catch (AuditContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Audit cursor could not be encoded", exception);
        }
    }

    @Override
    public Position decode(String tenantId, AuditQueryFilter filter, String cursor) {
        requireContext(tenantId, filter);
        if (cursor == null || cursor.isBlank()) {
            throw invalid("Audit cursor is required", null);
        }
        try {
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw invalid("Audit cursor format is invalid", null);
            }
            if (!matchesHmac("cursor:" + parts[0], DECODER.decode(parts[1]))) {
                throw invalid("Audit cursor signature is invalid", null);
            }
            JsonNode payload = objectMapper.readTree(DECODER.decode(parts[0]));
            if (payload.path("v").asInt(-1) != VERSION
                    || !tenantId.equals(payload.path("tenant").asString())
                    || !matchesFilterDigest(filter, payload.path("filter").asString())
                    || payload.path("expiresAt").asLong(0) <= clock.instant().getEpochSecond()) {
                throw invalid("Audit cursor context is invalid", null);
            }
            return new Position(
                    Instant.parse(payload.path("occurredAt").asString()),
                    UUID.fromString(payload.path("auditId").asString()));
        } catch (AuditContractException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Audit cursor is invalid", exception);
        }
    }

    private String filterDigest(AuditQueryFilter filter, byte[] key) {
        String canonical = String.join("|",
                component(filter.domain()), component(filter.action()), component(filter.actorId()),
                component(filter.targetType()), component(filter.targetId()), component(filter.from()),
                component(filter.to()), Integer.toString(filter.limit()));
        return ENCODER.encodeToString(hmac(key, "filter:" + canonical));
    }

    private static String component(Object value) {
        String text = value == null ? "" : value.toString();
        return text.length() + ":" + text;
    }

    private boolean matchesHmac(String value, byte[] provided) {
        return Arrays.stream(keys)
                .map(key -> hmac(key, value))
                .anyMatch(expected -> MessageDigest.isEqual(expected, provided));
    }

    private boolean matchesFilterDigest(AuditQueryFilter filter, String provided) {
        return Arrays.stream(keys).anyMatch(key -> filterDigest(filter, key).equals(provided));
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Audit cursor signer is unavailable", exception);
        }
    }

    private void requireContext(String tenantId, AuditQueryFilter filter) {
        if (tenantId == null || tenantId.isBlank() || filter == null) {
            throw invalid("Audit cursor context is invalid", null);
        }
    }

    private AuditContractException invalid(String message, Throwable cause) {
        return cause == null
                ? new AuditContractException(AuditErrorCode.INVALID_CURSOR, message)
                : new AuditContractException(AuditErrorCode.INVALID_CURSOR, message, cause);
    }
}
