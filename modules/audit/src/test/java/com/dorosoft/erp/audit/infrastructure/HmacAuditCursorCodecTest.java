package com.dorosoft.erp.audit.infrastructure;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.api.AuditQueryFilter;
import com.dorosoft.erp.audit.domain.AuditDomain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HmacAuditCursorCodecTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);
    private final HmacAuditCursorCodec codec =
            new HmacAuditCursorCodec(new byte[32], clock, Duration.ofMinutes(15), new ObjectMapper());
    private final AuditQueryFilter filter = new AuditQueryFilter(
            AuditDomain.IDENTITY, null, null, null, null,
            Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-04T00:00:00Z"), 20, null);

    @Test
    void roundTripsOnlyForTheSameTenantAndFilter() {
        Instant occurredAt = Instant.parse("2026-08-03T12:00:00Z");
        UUID auditId = UUID.randomUUID();
        String cursor = codec.encode("tenant-a", filter, occurredAt, auditId);

        Assertions.assertEquals(
                new com.dorosoft.erp.audit.application.port.AuditCursorCodec.Position(occurredAt, auditId),
                codec.decode("tenant-a", filter, cursor));
        AuditContractException exception = Assertions.assertThrows(
                AuditContractException.class,
                () -> codec.decode("tenant-b", filter, cursor));
        Assertions.assertEquals(AuditErrorCode.INVALID_CURSOR, exception.code());
    }

    @Test
    void rejectsTampering() {
        String cursor = codec.encode(
                "tenant-a", filter, Instant.parse("2026-08-03T12:00:00Z"), UUID.randomUUID());
        String tampered = cursor.substring(0, cursor.length() - 1)
                + (cursor.endsWith("A") ? "B" : "A");

        Assertions.assertThrows(
                AuditContractException.class,
                () -> codec.decode("tenant-a", filter, tampered));
    }

    @Test
    void acceptsAStillValidCursorSignedByThePreviousKey() {
        byte[] oldKey = new byte[32];
        byte[] newKey = new byte[32];
        Arrays.fill(oldKey, (byte) 1);
        Arrays.fill(newKey, (byte) 2);
        var oldCodec = new HmacAuditCursorCodec(
                oldKey, clock, Duration.ofMinutes(15), new ObjectMapper());
        var rotatingCodec = new HmacAuditCursorCodec(
                newKey, oldKey, clock, Duration.ofMinutes(15), new ObjectMapper());
        Instant occurredAt = Instant.parse("2026-08-03T12:00:00Z");
        UUID auditId = UUID.randomUUID();

        String cursor = oldCodec.encode("tenant-a", filter, occurredAt, auditId);

        Assertions.assertEquals(
                new com.dorosoft.erp.audit.application.port.AuditCursorCodec.Position(occurredAt, auditId),
                rotatingCodec.decode("tenant-a", filter, cursor));
    }
}
