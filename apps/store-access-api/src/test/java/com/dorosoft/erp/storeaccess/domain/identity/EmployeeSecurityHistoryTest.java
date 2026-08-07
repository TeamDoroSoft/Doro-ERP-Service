package com.dorosoft.erp.storeaccess.domain.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeSecurityHistoryTest {

    @Test
    void occurSetsExpiresAtNinetyDaysAfterOccurredAt() {
        Instant occurredAt = Instant.parse("2026-08-07T00:00:00Z");

        EmployeeSecurityHistory history = EmployeeSecurityHistory.occur(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SecurityHistoryEventType.EMPLOYEE_LOGIN_FAILED, null, null, null,
                SecurityHistoryResult.FAILURE, "INVALID_CREDENTIALS", null, null, occurredAt);

        assertThat(history.expiresAt()).isEqualTo(occurredAt.plus(Duration.ofDays(90)));
    }
}
