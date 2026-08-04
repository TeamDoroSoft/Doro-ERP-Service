package com.dorosoft.erp.table.infrastructure.persistence;

import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository;
import java.time.Instant;
import java.util.Optional;
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
                saved.getClosedAt(),
                saved.getVersion());
    }

    @Override
    @Transactional
    public Optional<TableUsageSessionSnapshot> findByTableIdAndSessionIdForUpdate(UUID tableId, UUID sessionId) {
        return repository.findByTableIdAndSessionIdForUpdate(tableId, sessionId).map(this::toSnapshot);
    }

    @Override
    @Transactional
    public TableUsageSessionSnapshot closeSession(
            UUID tableId,
            UUID sessionId,
            UUID closedBy,
            Instant closedAt,
            String closeReason) {
        TableUsageSessionEntity session =
                repository.findByTableIdAndSessionIdForUpdate(tableId, sessionId).orElseThrow();
        session.close(closedBy, closedAt, closeReason);
        return toSnapshot(repository.saveAndFlush(session));
    }

    private TableUsageSessionSnapshot toSnapshot(TableUsageSessionEntity session) {
        return new TableUsageSessionSnapshot(
                session.getSessionId(),
                session.getTableId(),
                session.getStatus().name(),
                session.getOpenedAt(),
                session.getClosedAt(),
                session.getVersion());
    }
}
