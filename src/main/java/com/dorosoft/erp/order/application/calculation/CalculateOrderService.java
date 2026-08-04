package com.dorosoft.erp.order.application.calculation;

import com.dorosoft.erp.catalog.application.api.OrderCatalogReader;
import com.dorosoft.erp.catalog.application.api.OrderCatalogSnapshot;
import com.dorosoft.erp.order.application.port.OrderRepository;
import com.dorosoft.erp.order.domain.item.InvalidOrderItemsException;
import com.dorosoft.erp.order.domain.item.InvalidQuantityException;
import com.dorosoft.erp.order.domain.item.OrderItem;
import com.dorosoft.erp.order.domain.item.OrderItemOption;
import com.dorosoft.erp.order.domain.order.EmptyOrderException;
import com.dorosoft.erp.order.domain.order.Order;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 항목의 Catalog Snapshot을 조회해 확정된 add()·multiply() 정책(OrderPriceCalculator)으로 금액을
 * 계산하고, 주문 시점 상품명·가격·옵션·수량·금액 Snapshot을 저장한다(06 공통 주문 관리 금액 공식 Slice).
 *
 * <p>채널·이행 유형·재고 차감·멱등성·Table Session 등 나머지 06 주문 생성 규칙은 이 Slice의 범위가 아니다.
 * 판정 순서는 06 명세의 우선순위를 따라 빈 주문·수량 검증(구조 오류)을 Catalog 조회보다 먼저 수행한다.
 */
@Service
public class CalculateOrderService {

    private final OrderCatalogReader orderCatalogReader;
    private final OrderRepository orderRepository;

    public CalculateOrderService(OrderCatalogReader orderCatalogReader, OrderRepository orderRepository) {
        this.orderCatalogReader = orderCatalogReader;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order calculate(CalculateOrderCommand command) {
        if (command.items().isEmpty()) {
            throw new EmptyOrderException();
        }
        if (command.items().size() > Order.MAX_ITEMS) {
            throw new InvalidOrderItemsException("주문 항목은 최대 " + Order.MAX_ITEMS + "개까지 허용합니다");
        }

        Set<String> clientLineIds = new HashSet<>();
        for (OrderItemSelection selection : command.items()) {
            validateClientLineId(selection.clientLineId(), clientLineIds);
            if (selection.quantity() < OrderItem.MIN_QUANTITY || selection.quantity() > OrderItem.MAX_QUANTITY) {
                throw new InvalidQuantityException(selection.quantity());
            }
            if (new HashSet<>(selection.optionIds()).size() != selection.optionIds().size()) {
                throw new InvalidOrderItemsException("같은 주문 항목에서 optionId를 중복 선택할 수 없습니다");
            }
        }

        List<OrderItem> orderItems = command.items().stream().map(this::toOrderItem).toList();
        Order order = Order.create(UUID.randomUUID(), orderItems, Instant.now());
        return orderRepository.save(order);
    }

    private OrderItem toOrderItem(OrderItemSelection selection) {
        OrderCatalogSnapshot snapshot = orderCatalogReader.resolveForOrder(selection.productId(), selection.optionIds());

        List<OrderItemOption> options =
                snapshot.selectedOptions().stream()
                        .map(o -> new OrderItemOption(o.optionId(), o.optionName(), o.additionalPrice()))
                        .toList();

        return OrderItem.create(
                selection.clientLineId(),
                snapshot.productId(),
                snapshot.productName(),
                snapshot.baseUnitPrice(),
                options,
                selection.quantity(),
                snapshot.stockManaged(),
                snapshot.catalogRevision());
    }

    private static void validateClientLineId(String clientLineId, Set<String> clientLineIds) {
        if (clientLineId == null || clientLineId.isBlank() || clientLineId.length() > OrderItem.MAX_CLIENT_LINE_ID_LENGTH) {
            throw new InvalidOrderItemsException(
                    "clientLineId는 1~" + OrderItem.MAX_CLIENT_LINE_ID_LENGTH + "자의 식별자여야 합니다");
        }
        if (!clientLineIds.add(clientLineId)) {
            throw new InvalidOrderItemsException("같은 주문에서 clientLineId를 중복 사용할 수 없습니다");
        }
    }
}
