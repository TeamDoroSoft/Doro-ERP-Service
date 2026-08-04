package com.dorosoft.erp.table.infrastructure.persistence;

import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaTableUsageSessionCommandRepository implements TableUsageSessionCommandRepository {

    private final TableUsageSessionJpaRepository repository;

    JpaTableUsageSessionCommandRepository(TableUsageSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsOpenSession(UUID tableId) {
        return repository.existsByTableIdAndStatus(tableId, TableUsageSessionStatus.OPEN);
    }

    @Override
    @Transactional
    public TableUsageSessionSnapshot openSession(UUID sessionId, UUID tableId, UUID openedBy, Instant openedAt) {
        TableUsageSessionEntity saved =
                repository.saveAndFlush(TableUsageSessionEntity.open(sessionId, tableId, openedBy, openedAt));
        return new TableUsageSessionSnapshot(
                saved.getSessionId(),
                saved.getTableId(),
                saved.getStatus().name(),
                saved.getOpenedAt(),
                saved.getVersion());
    }
}
