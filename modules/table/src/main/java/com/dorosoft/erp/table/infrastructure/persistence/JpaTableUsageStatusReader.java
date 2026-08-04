package com.dorosoft.erp.table.infrastructure.persistence;

import com.dorosoft.erp.table.application.port.TableUsageStatusReader;
import com.dorosoft.erp.table.domain.TableUsageStatus;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaTableUsageStatusReader implements TableUsageStatusReader {

    private final TableUsageSessionJpaRepository repository;

    JpaTableUsageStatusReader(TableUsageSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public TableUsageStatus getUsageStatus(UUID tableId) {
        return getUsageStatuses(List.of(tableId)).getOrDefault(tableId, TableUsageStatus.VACANT);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, TableUsageStatus> getUsageStatuses(Collection<UUID> tableIds) {
        Map<UUID, TableUsageStatus> statuses = new LinkedHashMap<>();
        if (tableIds == null || tableIds.isEmpty()) {
            return statuses;
        }

        Set<UUID> openTableIds =
                Set.copyOf(
                        repository.findTableIdsByStatusAndTableIdIn(
                                TableUsageSessionStatus.OPEN, tableIds));
        for (UUID tableId : tableIds) {
            statuses.put(
                    tableId,
                    openTableIds.contains(tableId) ? TableUsageStatus.OCCUPIED : TableUsageStatus.VACANT);
        }
        return statuses;
    }
}
