package com.dorosoft.erp.table.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TableUsageSessionCommandRepository {

    boolean existsOpenSession(UUID tableId);

    TableUsageSessionSnapshot openSession(UUID sessionId, UUID tableId, UUID openedBy, Instant openedAt);

    Optional<TableUsageSessionSnapshot> findByTableIdAndSessionIdForUpdate(UUID tableId, UUID sessionId);

    TableUsageSessionSnapshot closeSession(
            UUID tableId,
            UUID sessionId,
            UUID closedBy,
            Instant closedAt,
            String closeReason);

    record TableUsageSessionSnapshot(
            UUID sessionId,
            UUID tableId,
            String status,
            Instant openedAt,
            Instant closedAt,
            long version) {}
}
