package com.dorosoft.erp.table.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TableOrderItemSummaryResponse(
        UUID productId,
        String productName,
        int quantity,
        BigDecimal lineAmount) {}
