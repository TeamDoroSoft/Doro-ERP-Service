package com.dorosoft.erp.table.application.port;

import java.util.Optional;
import java.util.UUID;

public interface TableOpenSessionReader {

    Optional<TableOpenSessionSnapshot> findOpenSession(UUID tableId);

    record TableOpenSessionSnapshot(UUID sessionId, UUID tableId) {}
}
