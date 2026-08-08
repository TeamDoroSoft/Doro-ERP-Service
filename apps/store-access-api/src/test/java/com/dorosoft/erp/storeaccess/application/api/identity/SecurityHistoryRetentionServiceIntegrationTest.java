package com.dorosoft.erp.storeaccess.application.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies expired local security history is deleted in a limited batch, never all at once, and that
 * unexpired records are left untouched (ADR-02-015).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
                + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration"
})
@Testcontainers
class SecurityHistoryRetentionServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void identitySecrets(DynamicPropertyRegistry registry) {
        registry.add("doro.identity.hmac.rate-limit-secret", () -> "test-rate-limit-secret");
        registry.add("doro.identity.hmac.idempotency-secret", () -> "test-idempotency-secret");
        registry.add("doro.identity.hmac.kiosk-credential-secret", () -> "test-kiosk-credential-secret");
    }

    @Autowired
    private SecurityHistoryRetentionService retentionService;

    @Autowired
    private EmployeeSecurityHistoryRepository repository;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void deletesOnlyExpiredRecordsAndRespectsTheBatchLimit() {
        Instant now = clock.instant();
        UUID expired1 = saveWithExpiresAt(now.minus(Duration.ofDays(1))).id();
        UUID expired2 = saveWithExpiresAt(now.minus(Duration.ofDays(2))).id();
        UUID expired3 = saveWithExpiresAt(now.minus(Duration.ofDays(3))).id();
        UUID notExpired = saveWithExpiresAt(now.plus(Duration.ofDays(1))).id();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        int firstBatchDeleted = transactionTemplate.execute(status -> repository.deleteExpiredBatch(now, 2));

        assertThat(firstBatchDeleted).isEqualTo(2);
        long remainingExpired = countPresent(expired1, expired2, expired3);
        assertThat(remainingExpired).isEqualTo(1);
        assertThat(repository.findById(notExpired)).isPresent();

        int secondBatchDeleted = transactionTemplate.execute(status -> repository.deleteExpiredBatch(now, 10));

        assertThat(secondBatchDeleted).isEqualTo(1);
        assertThat(countPresent(expired1, expired2, expired3)).isZero();
        assertThat(repository.findById(notExpired)).isPresent();
    }

    @Test
    void deleteExpiredBatchServiceUsesTheConfiguredBatchSize() {
        Instant now = clock.instant();
        for (int i = 0; i < 3; i++) {
            saveWithExpiresAt(now.minus(Duration.ofDays(1)));
        }

        int deleted = retentionService.deleteExpiredBatch();

        assertThat(deleted).isEqualTo(3);
    }

    private long countPresent(UUID... ids) {
        long count = 0;
        for (UUID id : ids) {
            if (repository.findById(id).isPresent()) {
                count++;
            }
        }
        return count;
    }

    private EmployeeSecurityHistory saveWithExpiresAt(Instant expiresAt) {
        EmployeeSecurityHistory history = new EmployeeSecurityHistory(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SecurityHistoryEventType.EMPLOYEE_CREATED, UUID.randomUUID(), "EMPLOYEE", UUID.randomUUID(),
                SecurityHistoryResult.SUCCESS, null, null, null, expiresAt.minus(Duration.ofDays(90)), expiresAt);
        return repository.save(history);
    }
}
