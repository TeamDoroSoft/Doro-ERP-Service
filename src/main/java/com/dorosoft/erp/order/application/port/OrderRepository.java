package com.dorosoft.erp.order.application.port;

import com.dorosoft.erp.order.domain.order.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID orderId);
}
