package com.dorosoft.erp.order.application.calculation;

import com.dorosoft.erp.order.domain.item.InvalidOrderItemsException;
import java.util.List;
import java.util.Objects;

public record CalculateOrderCommand(List<OrderItemSelection> items) {

    public CalculateOrderCommand {
        if (items == null) {
            items = List.of();
        } else {
            if (items.stream().anyMatch(Objects::isNull)) {
                throw new InvalidOrderItemsException("주문 Item은 필수입니다");
            }
            items = List.copyOf(items);
        }
    }
}
