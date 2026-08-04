package com.dorosoft.erp.order.domain.order;

import com.dorosoft.erp.order.domain.item.OrderItem;
import com.dorosoft.erp.order.domain.money.OrderAmount;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 주문 Aggregate Root. 신규 생성과 저장된 Snapshot 복원 경로를 분리한다. */
public final class Order {

    private final UUID orderId;
    private final List<OrderItem> items;
    private final OrderAmount totalAmount;
    private final Instant createdAt;

    private Order(UUID orderId, List<OrderItem> items, OrderAmount totalAmount, Instant createdAt) {
        this.orderId = Objects.requireNonNull(orderId, "orderId는 필수다");
        if (items == null || items.isEmpty()) {
            throw new EmptyOrderException();
        }
        this.items = List.copyOf(items);
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount는 필수다");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt은 필수다");
    }

    /** Item 목록의 lineTotal을 OrderAmount.add()로 합산해 totalAmount를 계산한다. */
    public static Order create(UUID orderId, List<OrderItem> items, Instant createdAt) {
        OrderAmount total = OrderAmount.of(0L);
        for (OrderItem item : items) {
            total = total.add(item.price().lineTotal());
        }
        return new Order(orderId, items, total, createdAt);
    }

    /** 영속화된 주문 시점 총액을 재계산하지 않고 그대로 복원한다. */
    public static Order restore(UUID orderId, List<OrderItem> items, long totalAmount, Instant createdAt) {
        return new Order(orderId, items, OrderAmount.of(totalAmount), createdAt);
    }

    public UUID orderId() {
        return orderId;
    }

    public List<OrderItem> items() {
        return items;
    }

    public OrderAmount totalAmount() {
        return totalAmount;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
