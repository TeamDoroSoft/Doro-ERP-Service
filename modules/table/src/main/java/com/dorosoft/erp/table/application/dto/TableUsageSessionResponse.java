package com.dorosoft.erp.table.application.dto;

import java.time.Instant;
import java.util.UUID;

public record TableUsageSessionResponse(
        UUID sessionId,
        UUID tableId,
        String status,
        Instant openedAt) {}
