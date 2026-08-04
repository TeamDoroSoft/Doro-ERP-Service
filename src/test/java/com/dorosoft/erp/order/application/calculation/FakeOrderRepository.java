package com.dorosoft.erp.order.application.calculation;

import com.dorosoft.erp.order.application.port.OrderRepository;
import com.dorosoft.erp.order.domain.order.Order;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트 전용 In-memory OrderRepository. */
class FakeOrderRepository implements OrderRepository {

    private final Map<UUID, Order> store = new LinkedHashMap<>();

    @Override
    public Order save(Order order) {
        store.put(order.orderId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return Optional.ofNullable(store.get(orderId));
    }
}
