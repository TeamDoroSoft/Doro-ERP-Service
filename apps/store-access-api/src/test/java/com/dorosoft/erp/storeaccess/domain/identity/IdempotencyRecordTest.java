package com.dorosoft.erp.storeaccess.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    private final Instant now = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void claimStartsInProgressWithoutResult() {
        IdempotencyRecord record = IdempotencyRecord.claim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CREATE_EMPLOYEE",
                "key-digest", "request-hmac", now, now.plus(Duration.ofHours(24)));

        assertThat(record.status()).isEqualTo(IdempotencyRecordStatus.IN_PROGRESS);
        assertThat(record.resultPayload()).isNull();
        assertThat(record.completedAt()).isNull();
    }

    @Test
    void completeTransitionsToCompletedWithResultAndTimestamp() {
        IdempotencyRecord claimed = IdempotencyRecord.claim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CREATE_EMPLOYEE",
                "key-digest", "request-hmac", now, now.plus(Duration.ofHours(24)));
        Instant completedAt = now.plusSeconds(1);

        IdempotencyRecord completed = claimed.complete("{\"employeeId\":\"abc\"}", completedAt);

        assertThat(completed.status()).isEqualTo(IdempotencyRecordStatus.COMPLETED);
        assertThat(completed.resultPayload()).isEqualTo("{\"employeeId\":\"abc\"}");
        assertThat(completed.completedAt()).isEqualTo(completedAt);
        assertThat(completed.createdAt()).isEqualTo(now);
    }

    @Test
    void rejectsCompletedStatusWithoutCompletedAt() {
        assertThatThrownBy(() -> new IdempotencyRecord(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CREATE_EMPLOYEE",
                "key-digest", "request-hmac", IdempotencyRecordStatus.COMPLETED, "{}", now, null,
                now.plus(Duration.ofHours(24))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
