package com.dorosoft.erp.table.application.port;

import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository.TableUsageSessionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableUsageSessionQueryRepository {

    Optional<TableUsageSessionSnapshot> findOpenSession(UUID tableId);

    Optional<TableUsageSessionSnapshot> findSession(UUID tableId, UUID sessionId);

    SessionHistoryPage findClosedSessions(SessionHistoryQuery query);

    record SessionHistoryQuery(
            UUID tableId,
            Instant from,
            Instant to,
            SessionHistoryCursor cursor,
            int size) {}

    record SessionHistoryCursor(Instant closedAt, UUID sessionId) {}

    record SessionHistoryPage(List<TableUsageSessionSnapshot> items, boolean hasNext) {
        public SessionHistoryPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
