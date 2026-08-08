package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes logically-expired local security history in limited batches (ADR-02-015: "매일 제한된 Batch로
 * 삭제"), never as one unbounded delete or a long-running transaction. Callable directly for now; wiring an
 * actual daily trigger (e.g. {@code @Scheduled}, with multi-instance locking if the service ever runs more
 * than one instance) is a deployment decision left for later.
 *
 * <p>Constructed via a {@code @Bean} factory method in infrastructure config rather than component-scanned,
 * since its {@code batchSize} comes from a config-properties value that Application-layer code must not
 * depend on directly.
 */
public class SecurityHistoryRetentionService {

    private final EmployeeSecurityHistoryRepository repository;
    private final Clock clock;
    private final int batchSize;

    public SecurityHistoryRetentionService(EmployeeSecurityHistoryRepository repository, Clock clock, int batchSize) {
        this.repository = repository;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Transactional
    public int deleteExpiredBatch() {
        return repository.deleteExpiredBatch(clock.instant(), batchSize);
    }
}
