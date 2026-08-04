package com.dorosoft.erp.identity.domain.idempotency;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityIdempotencyRecordTest {
    @Test
    void processingStateCanOnlyBecomeAValid201Or204Success() {
        IdentityIdempotencyRecord processing = processing();
        assertTrue(processing.hasSameRequestHmac(bytes(2)));

        IdentityIdempotencyRecord created = processing.succeed(
                UUID.randomUUID(), 201, new byte[]{9}, new byte[12], Instant.parse("2026-08-04T00:00:01Z")
        );
        assertTrue(created.status() == IdempotencyStatus.SUCCEEDED);
        assertThrows(IllegalArgumentException.class, () -> processing.succeed(
                UUID.randomUUID(), 204, new byte[]{9}, new byte[12], Instant.now()
        ));
    }

    @Test
    void expirationBoundaryIsExactlyTwentyFourHours() {
        IdentityIdempotencyRecord record = processing();
        assertFalse(record.isExpiredAt(Instant.parse("2026-08-04T23:59:59.999999Z")));
        assertTrue(record.isExpiredAt(Instant.parse("2026-08-05T00:00:00Z")));
    }

    private static IdentityIdempotencyRecord processing() {
        return IdentityIdempotencyRecord.processing(
                UUID.randomUUID(), IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE,
                bytes(1), bytes(2), UUID.randomUUID(), "v1", Instant.parse("2026-08-04T00:00:00Z")
        );
    }

    private static byte[] bytes(int value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
