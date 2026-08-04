package com.dorosoft.erp.table.application.dto;

import java.util.List;

public record TableSessionHistoryPageResponse(
        List<TableOrderPageResponse.SessionSummary> items,
        String nextCursor) {

    public TableSessionHistoryPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
