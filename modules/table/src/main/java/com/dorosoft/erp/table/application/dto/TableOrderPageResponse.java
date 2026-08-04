package com.dorosoft.erp.table.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TableOrderPageResponse(
        SessionSummary session,
        List<TableOrderSummaryResponse> items,
        String nextCursor) {

    public TableOrderPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record SessionSummary(
            UUID sessionId,
            UUID tableId,
            Instant openedAt,
            Instant closedAt,
            String status) {}
}
