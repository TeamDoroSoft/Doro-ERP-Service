package com.dorosoft.erp.table.infrastructure.persistence;

import com.dorosoft.erp.table.application.port.TableOpenSessionReader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaTableOpenSessionReader implements TableOpenSessionReader {

    private final TableUsageSessionJpaRepository repository;

    JpaTableOpenSessionReader(TableUsageSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TableOpenSessionSnapshot> findOpenSession(UUID tableId) {
        return repository
                .findOpenSessionIdByTableId(tableId)
                .map(sessionId -> new TableOpenSessionSnapshot(sessionId, tableId));
    }
}
