package com.dorosoft.erp.audit.application.api.ingest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.platform.messaging.audit.AuditActor;
import com.dorosoft.erp.platform.messaging.audit.AuditEventEnvelope;
import com.dorosoft.erp.platform.messaging.audit.AuditTarget;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventContractValidatorTest {

    private final AuditEventContractValidator validator = new AuditEventContractValidator();

    @Test
    void versionOneUnknownActionAndAllowlistedMetadataAreAccepted() {
        AuditEventEnvelope event = event(1, "FUTURE_ACTION", Map.of("futureField", "value"), "req-123");

        assertThatCode(() -> validator.validate(event)).doesNotThrowAnyException();
    }

    @Test
    void unsupportedVersionIsRejectedBeforePersistence() {
        AuditEventEnvelope event = event(2, "ORDER_ACCEPTED", Map.of(), "req-123");

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void malformedTraceIdIsRejectedBeforePersistence() {
        AuditEventEnvelope event = event(1, "ORDER_ACCEPTED", Map.of(), "trace id with spaces");

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceId");
    }

    @Test
    void requestSystemAndMaximumLengthTraceIdsAreAccepted() {
        assertThatCode(() -> validator.validate(event(1, "ORDER_ACCEPTED", Map.of(), "req-123")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(event(
                        1,
                        "ORDER_ACCEPTED",
                        Map.of(),
                        "sys-10000000-0000-0000-0000-000000000001")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(event(1, "ORDER_ACCEPTED", Map.of(), "a".repeat(64))))
                .doesNotThrowAnyException();
    }

    @Test
    void traceIdLongerThanSixtyFourCharactersIsRejected() {
        AuditEventEnvelope event = event(1, "ORDER_ACCEPTED", Map.of(), "a".repeat(65));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceId");
    }

    @Test
    void recursivelyNestedSensitiveMetadataKeyIsRejectedWithoutEchoingItsValue() {
        AuditEventEnvelope event = event(
                1,
                "ORDER_ACCEPTED",
                Map.of("safe", Map.of("Authorization_Token", "do-not-log-this")),
                "req-123");

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metadata contains a forbidden key")
                .hasMessageNotContaining("do-not-log-this");
    }

    private AuditEventEnvelope event(int version, String action, Map<String, Object> metadata, String traceId) {
        return new AuditEventEnvelope(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "AuditRecorded",
                version,
                "commerce",
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                null,
                new AuditActor(
                        "SYSTEM",
                        UUID.fromString("00000000-0000-0000-0000-000000000000"),
                        null),
                action,
                new AuditTarget(
                        "ORDER",
                        UUID.fromString("50000000-0000-0000-0000-000000000005")),
                "SUCCESS",
                null,
                metadata,
                traceId,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
