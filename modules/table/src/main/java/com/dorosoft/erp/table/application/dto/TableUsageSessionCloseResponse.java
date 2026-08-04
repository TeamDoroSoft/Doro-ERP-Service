package com.dorosoft.erp.table.application.dto;

import java.time.Instant;
import java.util.UUID;

public record TableUsageSessionCloseResponse(
        UUID sessionId,
        UUID tableId,
        Instant openedAt,
        Instant closedAt,
        String status) {}
