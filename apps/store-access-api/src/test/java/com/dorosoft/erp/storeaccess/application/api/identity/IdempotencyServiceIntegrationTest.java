package com.dorosoft.erp.storeaccess.application.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.IdempotencyDigestSigner;
import com.dorosoft.erp.storeaccess.application.port.identity.IdempotencyRecordRepository;
import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the Idempotency-Key claim/replay/conflict contract (ADR-02-014) using a trivial in-test
 * operation, independent of any specific business use case.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
                + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration"
})
@Testcontainers
class IdempotencyServiceIntegrationTest {

    private static final String OPERATION = "TEST_OPERATION";

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
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private IdempotencyDigestSigner digestSigner;

    @Autowired
    private Clock clock;

    @Test
    void firstCallExecutesTheOperationExactlyOnce() {
        AtomicInteger executions = new AtomicInteger();

        String result = idempotencyService.execute(
                UUID.randomUUID(), UUID.randomUUID(), OPERATION, UUID.randomUUID().toString(), "payload-a",
                Function.identity(), Function.identity(), () -> "result-" + executions.incrementAndGet());

        assertThat(result).isEqualTo("result-1");
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void sameKeyAndPayloadReplaysTheStoredResultWithoutRunningTheOperationAgain() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();

        String first = idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload-a",
                Function.identity(), Function.identity(), () -> "result-" + executions.incrementAndGet());
        String second = idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload-a",
                Function.identity(), Function.identity(), () -> "result-" + executions.incrementAndGet());

        assertThat(second).isEqualTo(first);
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void sameKeyWithADifferentPayloadIsRejectedAsReused() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload-a", Function.identity(), Function.identity(), () -> "result");

        assertThatThrownBy(() -> idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload-b",
                Function.identity(), Function.identity(), () -> "result-2"))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(IdempotencyProblemCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void differentActorsCanReuseTheSameRawKeyWithoutConflict() {
        UUID tenantId = UUID.randomUUID();
        String key = "a-key-two-actors-happen-to-share";

        String resultA = idempotencyService.execute(
                tenantId, UUID.randomUUID(), OPERATION, key, "payload",
                Function.identity(), Function.identity(), () -> "from-actor-a");
        String resultB = idempotencyService.execute(
                tenantId, UUID.randomUUID(), OPERATION, key, "payload",
                Function.identity(), Function.identity(), () -> "from-actor-b");

        assertThat(resultA).isEqualTo("from-actor-a");
        assertThat(resultB).isEqualTo("from-actor-b");
    }

    @Test
    void failedOperationRollsBackTheClaimSoTheSameKeyCanBeRetried() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        assertThatThrownBy(() -> idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload", Function.identity(), Function.identity(),
                () -> {
                    throw new IllegalStateException("business logic failed");
                }))
                .isInstanceOf(IllegalStateException.class);

        String keyDigest = digestSigner.digestKey(key);
        assertThat(idempotencyRecordRepository.findByScope(tenantId, actorId, OPERATION, keyDigest)).isEmpty();

        String retried = idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload", Function.identity(), Function.identity(),
                () -> "succeeded-on-retry");
        assertThat(retried).isEqualTo("succeeded-on-retry");
    }

    @Test
    void inProgressClaimForTheSameKeyAndPayloadIsRejectedForRetryLater() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String canonicalRequest = "payload";
        String keyDigest = digestSigner.digestKey(key);
        String requestHmac = digestSigner.digestRequest(canonicalRequest);
        Instant now = clock.instant();
        idempotencyRecordRepository.save(IdempotencyRecord.claim(
                UUID.randomUUID(), tenantId, actorId, OPERATION, keyDigest, requestHmac, now,
                now.plus(Duration.ofHours(24))));

        assertThatThrownBy(() -> idempotencyService.execute(
                tenantId, actorId, OPERATION, key, canonicalRequest,
                Function.identity(), Function.identity(), () -> "should-not-run"))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(IdempotencyProblemCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    @Test
    void concurrentRequestsWithTheSameKeyAndPayloadBothReceiveTheSameSuccessfulResult() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        AtomicInteger executions = new AtomicInteger();
        Callable<String> attempt = () -> idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload",
                Function.identity(), Function.identity(), () -> "result-" + executions.incrementAndGet());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> future1 = executor.submit(attempt);
            Future<String> future2 = executor.submit(attempt);

            String outcome1 = future1.get(15, TimeUnit.SECONDS);
            String outcome2 = future2.get(15, TimeUnit.SECONDS);

            assertThat(executions.get()).isEqualTo(1);
            assertThat(outcome1).isEqualTo(outcome2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRequestsWithTheSameKeyButDifferentPayloadsRejectTheLoserAsReused() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        Callable<String> attemptA = () -> idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload-a", Function.identity(), Function.identity(), () -> "result-a");
        Callable<String> attemptB = () -> idempotencyService.execute(
                tenantId, actorId, OPERATION, key, "payload-b", Function.identity(), Function.identity(), () -> "result-b");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> future1 = executor.submit(attemptA);
            Future<String> future2 = executor.submit(attemptB);

            Object outcome1 = resultOrFailure(future1);
            Object outcome2 = resultOrFailure(future2);

            long successCount = Stream.of(outcome1, outcome2).filter(String.class::isInstance).count();
            assertThat(successCount).isEqualTo(1);
            Object rejected = outcome1 instanceof String ? outcome2 : outcome1;
            assertThat(rejected)
                    .asInstanceOf(type(ProblemAwareException.class))
                    .extracting(ProblemAwareException::code)
                    .isEqualTo(IdempotencyProblemCode.IDEMPOTENCY_KEY_REUSED);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object resultOrFailure(Future<String> future) throws InterruptedException {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (TimeoutException e) {
            throw new AssertionError("Concurrent idempotent call did not complete in time", e);
        }
    }
}
