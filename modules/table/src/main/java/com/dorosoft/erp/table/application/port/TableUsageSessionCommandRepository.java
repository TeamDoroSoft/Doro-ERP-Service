package com.dorosoft.erp.table.application.port;

import java.time.Instant;
import java.util.UUID;

public interface TableUsageSessionCommandRepository {

    boolean existsOpenSession(UUID tableId);

    TableUsageSessionSnapshot openSession(UUID sessionId, UUID tableId, UUID openedBy, Instant openedAt);

    record TableUsageSessionSnapshot(
            UUID sessionId,
            UUID tableId,
            String status,
            Instant openedAt,
            long version) {}
}
