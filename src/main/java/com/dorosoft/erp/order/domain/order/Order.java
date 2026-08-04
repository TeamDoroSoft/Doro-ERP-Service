package com.dorosoft.erp.order.domain.order;

import com.dorosoft.erp.order.domain.item.OrderItem;
import com.dorosoft.erp.order.domain.money.OrderAmount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 주문 Aggregate Root. Item 줄 합계를 checked add로 합산한 총액을 갖는다(06 공통 주문 관리 금액 공식). */
public record Order(UUID orderId, List<OrderItem> items, OrderAmount totalAmount, Instant createdAt) {

    public Order {
        if (items == null || items.isEmpty()) {
            throw new EmptyOrderException();
        }
        items = List.copyOf(items);
    }

    /** Item 목록의 lineTotal을 OrderAmount.add()로 합산해 totalAmount를 계산한다. */
    public static Order create(UUID orderId, List<OrderItem> items, Instant createdAt) {
        OrderAmount total = OrderAmount.of(0L);
        for (OrderItem item : items) {
            total = total.add(item.price().lineTotal());
        }
        return new Order(orderId, items, total, createdAt);
    }
}
