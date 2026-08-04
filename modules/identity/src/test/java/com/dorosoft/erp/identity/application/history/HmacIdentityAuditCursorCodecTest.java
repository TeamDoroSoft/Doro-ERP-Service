package com.dorosoft.erp.identity.application.history;

import com.dorosoft.erp.identity.application.error.IdentityException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HmacIdentityAuditCursorCodecTest {
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final NormalizedIdentityAuditFilter FILTER = new NormalizedIdentityAuditFilter(
            null, null, null, NOW.minus(Duration.ofDays(30)), NOW, 20,
            NormalizedIdentityAuditFilter.EventTypeSource.ALL);

    @Test
    void roundTripsVersionTenantFilterAndAllSortKeys() {
        var codec = codec(NOW);
        var key = new IdentityAuditSortKey(
                Instant.parse("2026-08-03T11:59:59.123456Z"),
                IdentityAuditSourceType.AUDIT_RECORD,
                UUID.randomUUID());

        assertEquals(key, codec.decode("tenant-a", FILTER, codec.encode("tenant-a", FILTER, key)).lastKey());
    }

    @Test
    void rejectsSignatureTamperingTenantAndFilterReuse() {
        var codec = codec(NOW);
        var key = new IdentityAuditSortKey(NOW.minusSeconds(1),
                IdentityAuditSourceType.IDENTITY_SECURITY_EVENT, UUID.randomUUID());
        String cursor = codec.encode("tenant-a", FILTER, key);
        String tampered = cursor.substring(0, cursor.length() - 1)
                + (cursor.endsWith("A") ? "B" : "A");

        assertThrows(IdentityException.class,
                () -> codec.decode("tenant-a", FILTER, tampered));
        assertThrows(IdentityException.class,
                () -> codec.decode("tenant-b", FILTER, cursor));
        var anotherFilter = new NormalizedIdentityAuditFilter(
                "LOGIN_FAILED", null, null, FILTER.from(), FILTER.to(), 20,
                NormalizedIdentityAuditFilter.EventTypeSource.SECURITY);
        assertThrows(IdentityException.class,
                () -> codec.decode("tenant-a", anotherFilter, cursor));
    }

    @Test
    void rejectsExpiredCursorAndShortSecrets() {
        var codec = codec(NOW);
        String cursor = codec.encode("tenant-a", FILTER,
                new IdentityAuditSortKey(NOW, IdentityAuditSourceType.AUDIT_RECORD, UUID.randomUUID()));
        var later = codec(NOW.plus(Duration.ofMinutes(16)));

        assertThrows(IdentityException.class,
                () -> later.decode("tenant-a", FILTER, cursor));
        assertThrows(IllegalArgumentException.class,
                () -> new HmacIdentityAuditCursorCodec(new byte[31], Clock.systemUTC(), Duration.ofMinutes(15)));
    }

    @Test
    void acceptsAStillValidCursorSignedByThePreviousKey() {
        byte[] oldKey = filled((byte) 3);
        byte[] newKey = filled((byte) 4);
        var oldCodec = new HmacIdentityAuditCursorCodec(
                oldKey, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
        var rotatingCodec = new HmacIdentityAuditCursorCodec(
                newKey, oldKey, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
        var sortKey = new IdentityAuditSortKey(
                NOW.minusSeconds(1), IdentityAuditSourceType.AUDIT_RECORD, UUID.randomUUID());

        String cursor = oldCodec.encode("tenant-a", FILTER, sortKey);

        assertEquals(sortKey, rotatingCodec.decode("tenant-a", FILTER, cursor).lastKey());
    }

    private HmacIdentityAuditCursorCodec codec(Instant now) {
        byte[] key = filled((byte) 9);
        return new HmacIdentityAuditCursorCodec(
                key, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15));
    }

    private byte[] filled(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }
}
