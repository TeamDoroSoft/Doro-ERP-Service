package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/** Verifies the retry/backoff and interrupt handling contract without needing a real Redis (ADR-02-004). */
class SessionInvalidatorTest {

    @Test
    void succeedsOnFirstAttemptWhenNoFailureOccurs() {
        FakeSessionRepository repository = new FakeSessionRepository(0);
        SessionInvalidator invalidator = new SessionInvalidator(() -> repository, duration -> { });

        SessionInvalidationResult result = invalidator.invalidateAll(randomKey());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.attempts()).isEqualTo(1);
    }

    @Test
    void retriesTransientFailuresAndEventuallySucceeds() {
        FakeSessionRepository repository = new FakeSessionRepository(2);
        List<Duration> sleeps = new ArrayList<>();
        SessionInvalidator invalidator = new SessionInvalidator(() -> repository, sleeps::add);

        SessionInvalidationResult result = invalidator.invalidateAll(randomKey());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.attempts()).isEqualTo(3);
        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(300));
    }

    @Test
    void reportsFinalFailureAfterFourAttempts() {
        FakeSessionRepository repository = new FakeSessionRepository(Integer.MAX_VALUE);
        List<Duration> sleeps = new ArrayList<>();
        SessionInvalidator invalidator = new SessionInvalidator(() -> repository, sleeps::add);

        SessionInvalidationResult result = invalidator.invalidateAll(randomKey());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.attempts()).isEqualTo(4);
        assertThat(sleeps).containsExactly(Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofMillis(900));
    }

    @Test
    void interruptDuringBackoffStopsRetryingImmediately() {
        FakeSessionRepository repository = new FakeSessionRepository(Integer.MAX_VALUE);
        RetryBackoffSleeper interruptingSleeper = duration -> {
            throw new InterruptedException("stop");
        };
        SessionInvalidator invalidator = new SessionInvalidator(() -> repository, interruptingSleeper);

        SessionInvalidationResult result = invalidator.invalidateAll(randomKey());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(Thread.interrupted()).isTrue();
    }

    private SessionPrincipalIndexKey randomKey() {
        return new SessionPrincipalIndexKey(UUID.randomUUID(), UUID.randomUUID());
    }

    private static final class FakeSessionRepository implements FindByIndexNameSessionRepository<Session> {

        private final int failuresBeforeSuccess;
        private int callCount;

        FakeSessionRepository(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public Map<String, Session> findByIndexNameAndIndexValue(String indexName, String indexValue) {
            callCount++;
            if (callCount <= failuresBeforeSuccess) {
                throw new RuntimeException("simulated Redis failure");
            }
            return Map.of();
        }

        @Override
        public Session createSession() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Session session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Session findById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(String id) {
        }
    }
}
