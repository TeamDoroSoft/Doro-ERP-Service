package com.dorosoft.erp.order.application.calculation;

import java.util.List;

public record CalculateOrderCommand(List<OrderItemSelection> items) {

    public CalculateOrderCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
