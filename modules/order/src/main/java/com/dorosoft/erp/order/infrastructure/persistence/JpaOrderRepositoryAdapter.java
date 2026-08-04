package com.dorosoft.erp.order.infrastructure.persistence;

import com.dorosoft.erp.order.application.port.OrderRepository;
import com.dorosoft.erp.order.domain.item.OrderItem;
import com.dorosoft.erp.order.domain.item.OrderItemOption;
import com.dorosoft.erp.order.domain.order.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Repository;

/** JPA 엔티티와 도메인 Order Aggregate 사이의 변환을 전담한다. 엔티티는 이 패키지 밖으로 나가지 않는다. */
@Repository
public class JpaOrderRepositoryAdapter implements OrderRepository {

    private static final String KRW = "KRW";

    private final OrderJpaRepository jpaRepository;

    public JpaOrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = new OrderEntity(order.orderId(), order.totalAmount().amount(), KRW);
        entity.replaceItems(toItemEntities(order.items()));
        OrderEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return jpaRepository.findById(orderId).map(JpaOrderRepositoryAdapter::toDomain);
    }

    private static List<OrderItemEntity> toItemEntities(List<OrderItem> items) {
        return IntStream.range(0, items.size())
                .mapToObj(index -> toItemEntity(items.get(index), index))
                .toList();
    }

    private static OrderItemEntity toItemEntity(OrderItem item, int lineOrder) {
        OrderItemEntity entity =
                new OrderItemEntity(
                        item.lineId(),
                        item.clientLineId(),
                        lineOrder,
                        item.productId(),
                        item.productName(),
                        item.baseUnitPrice(),
                        item.price().optionUnitAmount().amount(),
                        item.price().unitPrice().amount(),
                        item.quantity(),
                        item.price().lineTotal().amount(),
                        item.stockManaged(),
                        item.catalogRevision());
        entity.replaceOptions(toOptionEntities(item.options()));
        return entity;
    }

    private static List<OrderItemOptionEntity> toOptionEntities(List<OrderItemOption> options) {
        return IntStream.range(0, options.size())
                .mapToObj(index -> {
                    OrderItemOption option = options.get(index);
                    return new OrderItemOptionEntity(
                            UUID.randomUUID(),
                            option.optionId(),
                            index,
                            option.optionName(),
                            option.additionalPrice());
                })
                .toList();
    }

    private static Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream().map(JpaOrderRepositoryAdapter::toItemDomain).toList();
        return Order.restore(entity.getOrderId(), items, entity.getTotalAmount(), entity.getCreatedAt());
    }

    private static OrderItem toItemDomain(OrderItemEntity entity) {
        List<OrderItemOption> options =
                entity.getOptions().stream()
                        .map(o -> new OrderItemOption(o.getOptionId(), o.getOptionName(), o.getAdditionalPrice()))
                        .toList();
        return OrderItem.restore(
                entity.getOrderItemId(),
                entity.getClientLineId(),
                entity.getProductId(),
                entity.getProductName(),
                entity.getBaseUnitPrice(),
                options,
                entity.getQuantity(),
                entity.getOptionUnitAmount(),
                entity.getUnitPrice(),
                entity.getLineAmount(),
                entity.isStockManaged(),
                entity.getCatalogRevision());
    }
}
