package com.dorosoft.erp.identity.application.history;

import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Constructor-injected active/previous cursor key ring shared with Feature 17 Audit policy. */
public final class HmacIdentityAuditCursorCodec implements IdentityAuditCursorCodec {
    private static final int VERSION = 1;
    private static final int MINIMUM_KEY_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[][] secrets;
    private final Clock clock;
    private final Duration timeToLive;
    private final ObjectMapper objectMapper;

    public HmacIdentityAuditCursorCodec(byte[] secret, Clock clock, Duration timeToLive) {
        this(secret, null, clock, timeToLive);
    }

    public HmacIdentityAuditCursorCodec(
            byte[] activeSecret,
            byte[] previousSecret,
            Clock clock,
            Duration timeToLive
    ) {
        if (activeSecret == null || activeSecret.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("Identity audit cursor key must contain at least 32 bytes");
        }
        if (previousSecret != null && previousSecret.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("Previous identity audit cursor key must contain at least 32 bytes");
        }
        if (previousSecret != null && MessageDigest.isEqual(activeSecret, previousSecret)) {
            throw new IllegalArgumentException("Identity audit cursor active and previous keys must differ");
        }
        if (timeToLive == null || timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("Identity audit cursor lifetime must be positive");
        }
        this.secrets = previousSecret == null
                ? new byte[][] {Arrays.copyOf(activeSecret, activeSecret.length)}
                : new byte[][] {
                    Arrays.copyOf(activeSecret, activeSecret.length),
                    Arrays.copyOf(previousSecret, previousSecret.length)
                };
        this.clock = Objects.requireNonNull(clock);
        this.timeToLive = timeToLive;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String encode(String tenantId, NormalizedIdentityAuditFilter filter, IdentityAuditSortKey lastKey) {
        requireTenant(tenantId);
        Objects.requireNonNull(filter);
        Objects.requireNonNull(lastKey);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", VERSION);
        payload.put("tenant", tenantId);
        payload.put("filter", filterDigest(filter, secrets[0]));
        payload.put("eventType", filter.eventType());
        payload.put("actorAccountId", text(filter.actorAccountId()));
        payload.put("targetAccountId", text(filter.targetAccountId()));
        payload.put("from", filter.from().toString());
        payload.put("to", filter.to().toString());
        payload.put("size", filter.size());
        payload.put("eventTypeSource", filter.eventTypeSource().name());
        payload.put("occurredAt", lastKey.occurredAt().toString());
        payload.put("sourceOrder", lastKey.sourceType().sourceOrder());
        payload.put("eventId", lastKey.eventId().toString());
        payload.put("expiresAt", clock.instant().plus(timeToLive).getEpochSecond());
        try {
            String encodedPayload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signature = ENCODER.encodeToString(hmac(secrets[0], "cursor:" + encodedPayload));
            return encodedPayload + "." + signature;
        } catch (Exception exception) {
            throw invalidCursor("Identity audit cursor could not be encoded", exception);
        }
    }

    @Override
    public DecodedCursor decode(String tenantId, NormalizedIdentityAuditFilter requestedFilter, String cursor) {
        requireTenant(tenantId);
        if (cursor == null || cursor.isBlank() || requestedFilter == null) {
            throw invalidCursor("Identity audit cursor is invalid", null);
        }
        try {
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw invalidCursor("Identity audit cursor is invalid", null);
            }
            byte[] provided = DECODER.decode(parts[1]);
            if (!matchesHmac("cursor:" + parts[0], provided)) {
                throw invalidCursor("Identity audit cursor signature is invalid", null);
            }
            JsonNode payload = objectMapper.readTree(DECODER.decode(parts[0]));
            NormalizedIdentityAuditFilter storedFilter = readFilter(payload);
            if (payload.path("v").asInt(-1) != VERSION
                    || !tenantId.equals(payload.path("tenant").asString())
                    || !matchesFilterDigest(storedFilter, payload.path("filter").asString())
                    || !matchesRequested(requestedFilter, storedFilter)
                    || payload.path("expiresAt").asLong(0) <= clock.instant().getEpochSecond()) {
                throw invalidCursor("Identity audit cursor context is invalid", null);
            }
            IdentityAuditSourceType source = source(payload.path("sourceOrder").asInt(-1));
            return new DecodedCursor(
                    storedFilter,
                    new IdentityAuditSortKey(
                            Instant.parse(payload.path("occurredAt").asString()),
                            source,
                            UUID.fromString(payload.path("eventId").asString())));
        } catch (IdentityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidCursor("Identity audit cursor is invalid", exception);
        }
    }

    private NormalizedIdentityAuditFilter readFilter(JsonNode payload) {
        return new NormalizedIdentityAuditFilter(
                nullableText(payload, "eventType"),
                nullableUuid(payload, "actorAccountId"),
                nullableUuid(payload, "targetAccountId"),
                Instant.parse(payload.path("from").asString()),
                Instant.parse(payload.path("to").asString()),
                payload.path("size").asInt(),
                NormalizedIdentityAuditFilter.EventTypeSource.valueOf(
                        payload.path("eventTypeSource").asString()));
    }

    private boolean matchesRequested(
            NormalizedIdentityAuditFilter requested,
            NormalizedIdentityAuditFilter stored
    ) {
        return Objects.equals(requested.eventType(), stored.eventType())
                && Objects.equals(requested.actorAccountId(), stored.actorAccountId())
                && Objects.equals(requested.targetAccountId(), stored.targetAccountId())
                && (requested.from() == null || requested.from().equals(stored.from()))
                && (requested.to() == null || requested.to().equals(stored.to()))
                && requested.size() == stored.size()
                && requested.eventTypeSource() == stored.eventTypeSource();
    }

    private String nullableText(JsonNode payload, String field) {
        String value = payload.path(field).asString();
        return value == null || value.isBlank() ? null : value;
    }

    private UUID nullableUuid(JsonNode payload, String field) {
        String value = nullableText(payload, field);
        return value == null ? null : UUID.fromString(value);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String filterDigest(NormalizedIdentityAuditFilter filter, byte[] secret) {
        String canonical = String.join("|",
                component(filter.eventType()),
                component(filter.actorAccountId()),
                component(filter.targetAccountId()),
                component(filter.from()),
                component(filter.to()),
                Integer.toString(filter.size()),
                filter.eventTypeSource().name());
        return ENCODER.encodeToString(hmac(secret, "filter:" + canonical));
    }

    private String component(Object value) {
        String text = value == null ? "" : value.toString();
        return text.length() + ":" + text;
    }

    private boolean matchesHmac(String value, byte[] provided) {
        return Arrays.stream(secrets)
                .map(secret -> hmac(secret, value))
                .anyMatch(expected -> MessageDigest.isEqual(expected, provided));
    }

    private boolean matchesFilterDigest(NormalizedIdentityAuditFilter filter, String provided) {
        return Arrays.stream(secrets)
                .anyMatch(secret -> filterDigest(filter, secret).equals(provided));
    }

    private byte[] hmac(byte[] secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Identity audit cursor signer is unavailable", exception);
        }
    }

    private IdentityAuditSourceType source(int order) {
        return Arrays.stream(IdentityAuditSourceType.values())
                .filter(value -> value.sourceOrder() == order)
                .findFirst()
                .orElseThrow(() -> invalidCursor("Identity audit cursor source is invalid", null));
    }

    private void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw invalidCursor("Identity audit cursor tenant is invalid", null);
        }
    }

    private IdentityException invalidCursor(String message, Throwable cause) {
        return cause == null
                ? new IdentityException(IdentityErrorCode.INVALID_CURSOR)
                : new IdentityException(IdentityErrorCode.INVALID_CURSOR, cause);
    }
}
