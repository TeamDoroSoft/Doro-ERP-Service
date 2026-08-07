package com.dorosoft.erp.platform.messaging.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class AuditEventEnvelopeJsonTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void versionOneEnvelopeRoundTripsWithoutConsumerOwnedTimestamps() throws Exception {
        AuditEventEnvelope envelope = sampleEnvelope();

        String json = jsonMapper.writeValueAsString(envelope);
        AuditEventEnvelope restored = jsonMapper.readValue(json, AuditEventEnvelope.class);

        assertThat(restored).isEqualTo(envelope);
        assertThat(json).doesNotContain("expiresAt", "receivedAt");
    }

    @Test
    void unknownTopLevelFieldsDoNotBreakForwardCompatibleDeserialization() throws Exception {
        String json = jsonMapper.writeValueAsString(sampleEnvelope());
        String withUnknownField = json.substring(0, json.length() - 1) + ",\"futureField\":\"kept-by-v2\"}";

        AuditEventEnvelope restored = jsonMapper.readValue(withUnknownField, AuditEventEnvelope.class);

        assertThat(restored).isEqualTo(sampleEnvelope());
    }

    private AuditEventEnvelope sampleEnvelope() {
        return new AuditEventEnvelope(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                "AuditRecorded",
                1,
                "commerce",
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                new AuditActor(
                        "EMPLOYEE",
                        UUID.fromString("40000000-0000-0000-0000-000000000004"),
                        "MANAGER"),
                "ORDER_ACCEPTED",
                new AuditTarget(
                        "ORDER",
                        UUID.fromString("50000000-0000-0000-0000-000000000005")),
                "SUCCESS",
                null,
                Map.of("orderNumber", "A-001"),
                "req-123",
                Instant.parse("2026-08-07T01:02:03Z"));
    }
}
