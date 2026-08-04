package com.dorosoft.erp.table.infrastructure.persistence;

import com.dorosoft.erp.table.application.port.TableUsageSessionCommandRepository.TableUsageSessionSnapshot;
import com.dorosoft.erp.table.application.port.TableUsageSessionQueryRepository;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaTableUsageSessionQueryRepository implements TableUsageSessionQueryRepository {

    private static final Comparator<TableUsageSessionSnapshot> HISTORY_ORDER =
            Comparator.comparing(TableUsageSessionSnapshot::closedAt)
                    .reversed()
                    .thenComparing(
                            snapshot -> snapshot.sessionId().toString(),
                            Comparator.reverseOrder());

    private final TableUsageSessionJpaRepository repository;

    JpaTableUsageSessionQueryRepository(TableUsageSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TableUsageSessionSnapshot> findOpenSession(UUID tableId) {
        return repository.findByTableIdAndStatus(tableId, TableUsageSessionStatus.OPEN).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TableUsageSessionSnapshot> findSession(UUID tableId, UUID sessionId) {
        return repository.findByTableIdAndSessionId(tableId, sessionId).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public SessionHistoryPage findClosedSessions(SessionHistoryQuery query) {
        var sessions =
                repository
                        .findByTableIdAndStatusAndClosedAtIsNotNull(
                                query.tableId(),
                                TableUsageSessionStatus.CLOSED,
                                Sort.by(Sort.Order.desc("closedAt"), Sort.Order.desc("sessionId")))
                        .stream()
                        .map(this::toSnapshot)
                        .filter(session -> query.from() == null || !session.closedAt().isBefore(query.from()))
                        .filter(session -> query.to() == null || !session.closedAt().isAfter(query.to()))
                        .filter(session -> isAfterCursor(session, query.cursor()))
                        .sorted(HISTORY_ORDER)
                        .limit((long) query.size() + 1)
                        .toList();
        boolean hasNext = sessions.size() > query.size();
        return new SessionHistoryPage(
                hasNext ? sessions.subList(0, query.size()) : sessions,
                hasNext);
    }

    private static boolean isAfterCursor(TableUsageSessionSnapshot session, SessionHistoryCursor cursor) {
        if (cursor == null) {
            return true;
        }
        int closedAtComparison = session.closedAt().compareTo(cursor.closedAt());
        if (closedAtComparison < 0) {
            return true;
        }
        if (closedAtComparison > 0) {
            return false;
        }
        return session.sessionId().toString().compareTo(cursor.sessionId().toString()) < 0;
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
