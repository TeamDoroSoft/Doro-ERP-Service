package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Deletes every Redis session registered under an employee's Session Principal Index Key
 * (ADR-02-002/004/005), retrying transient failures with backoff. Callers must invoke this only after their
 * own PostgreSQL transaction has committed; a failure here must never roll back a completed account change.
 *
 * <p>Resolves the session repository lazily through an {@link ObjectProvider} instead of a direct
 * constructor dependency, so this component can be registered unconditionally: contexts that never
 * configure Spring Session (e.g. Postgres-only test slices with no Redis available) can still start, and
 * only fail if {@link #invalidateAll} is actually called without a session repository present.
 */
@Component
public class SessionInvalidator {

    private static final Logger log = LoggerFactory.getLogger(SessionInvalidator.class);
    private static final int MAX_ATTEMPTS = 4;
    private static final List<Duration> RETRY_BACKOFFS =
            List.of(Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofMillis(900));

    private final Supplier<FindByIndexNameSessionRepository<? extends Session>> sessionRepositorySupplier;
    private final RetryBackoffSleeper sleeper;

    @Autowired
    public SessionInvalidator(
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessionRepositoryProvider) {
        this(sessionRepositoryProvider::getObject, RetryBackoffSleeper.threadSleep());
    }

    SessionInvalidator(
            Supplier<FindByIndexNameSessionRepository<? extends Session>> sessionRepositorySupplier,
            RetryBackoffSleeper sleeper) {
        this.sessionRepositorySupplier = sessionRepositorySupplier;
        this.sleeper = sleeper;
    }

    public SessionInvalidationResult invalidateAll(SessionPrincipalIndexKey key) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                deleteAll(key);
                return new SessionInvalidationResult(true, attempt);
            } catch (RuntimeException e) {
                if (attempt == MAX_ATTEMPTS) {
                    logFinalFailure(key, attempt, e);
                    return new SessionInvalidationResult(false, attempt);
                }
                try {
                    sleeper.sleep(RETRY_BACKOFFS.get(attempt - 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    logFinalFailure(key, attempt, e);
                    return new SessionInvalidationResult(false, attempt);
                }
            }
        }
        return new SessionInvalidationResult(false, MAX_ATTEMPTS);
    }

    private void deleteAll(SessionPrincipalIndexKey key) {
        FindByIndexNameSessionRepository<? extends Session> sessionRepository = sessionRepositorySupplier.get();
        Map<String, ? extends Session> sessions = sessionRepository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, key.value());
        sessions.keySet().forEach(sessionRepository::deleteById);
    }

    private void logFinalFailure(SessionPrincipalIndexKey key, int attempt, Exception cause) {
        log.warn(
                "Failed to invalidate employee sessions after {} attempts, principalIndexKey={}",
                attempt, key.value(), cause);
    }
}
