package com.dorosoft.erp.table.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TableOrderSummaryResponse(
        UUID orderId,
        String orderNumber,
        Instant createdAt,
        String status,
        BigDecimal totalAmount,
        String currency,
        String paymentStatus,
        List<TableOrderItemSummaryResponse> items) {

    public TableOrderSummaryResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
