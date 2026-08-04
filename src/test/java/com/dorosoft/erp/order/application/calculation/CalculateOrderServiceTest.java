package com.dorosoft.erp.order.application.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.catalog.application.api.OrderCatalogSnapshot;
import com.dorosoft.erp.catalog.application.api.SelectedOptionSnapshot;
import com.dorosoft.erp.order.domain.item.InvalidOrderItemsException;
import com.dorosoft.erp.order.domain.item.InvalidQuantityException;
import com.dorosoft.erp.order.domain.order.EmptyOrderException;
import com.dorosoft.erp.order.domain.order.Order;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CalculateOrderService - Catalog Snapshot 조회부터 Order Snapshot 저장까지(Fake Port)")
class CalculateOrderServiceTest {

    private FakeOrderCatalogReader catalogReader;
    private FakeOrderRepository orderRepository;
    private CalculateOrderService service;

    @BeforeEach
    void setUp() {
        catalogReader = new FakeOrderCatalogReader();
        orderRepository = new FakeOrderRepository();
        service = new CalculateOrderService(catalogReader, orderRepository);
    }

    @Test
    @DisplayName("Item이 없으면 EMPTY_ORDER를 던지고 저장하지 않는다")
    void rejectsEmptyCommand() {
        assertThatThrownBy(() -> service.calculate(new CalculateOrderCommand(List.of())))
                .isInstanceOf(EmptyOrderException.class);
    }

    @Test
    @DisplayName("quantity=0은 Catalog 조회 전에 INVALID_QUANTITY로 거부한다")
    void rejectsZeroQuantityBeforeCatalogLookup() {
        UUID unregisteredProductId = UUID.randomUUID();
        CalculateOrderCommand command =
                new CalculateOrderCommand(List.of(new OrderItemSelection("line-1", unregisteredProductId, List.of(), 0)));

        assertThatThrownBy(() -> service.calculate(command)).isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    @DisplayName("옵션 포함 여러 항목의 금액을 계산해 Order를 저장한다")
    void calculatesAndSavesOrder() {
        UUID productId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        catalogReader.register(
                new OrderCatalogSnapshot(
                        productId, "아메리카노", 4500L, List.of(new SelectedOptionSnapshot(optionId, "샷 추가", 500L)), false, 3L));

        CalculateOrderCommand command =
                new CalculateOrderCommand(List.of(new OrderItemSelection("line-1", productId, List.of(optionId), 2)));

        Order order = service.calculate(command);

        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).clientLineId()).isEqualTo("line-1");
        assertThat(order.items().get(0).price().unitPrice().amount()).isEqualTo(5000L);
        assertThat(order.items().get(0).price().lineTotal().amount()).isEqualTo(10000L);
        assertThat(order.totalAmount().amount()).isEqualTo(10000L);
        assertThat(orderRepository.findById(order.orderId())).contains(order);
    }

    @Test
    @DisplayName("Item이 100개를 넘으면 Catalog 조회 전에 INVALID_ORDER_ITEMS")
    void rejectsMoreThanOneHundredItems() {
        List<OrderItemSelection> items =
                IntStream.rangeClosed(1, 101)
                        .mapToObj(i -> new OrderItemSelection("line-" + i, UUID.randomUUID(), List.of(), 1))
                        .toList();

        assertThatThrownBy(() -> service.calculate(new CalculateOrderCommand(items)))
                .isInstanceOf(InvalidOrderItemsException.class);
    }

    @Test
    @DisplayName("Item 경계값 100개는 허용한다")
    void acceptsOneHundredItems() {
        UUID productId = UUID.randomUUID();
        catalogReader.register(new OrderCatalogSnapshot(productId, "아메리카노", 100L, List.of(), false, 1L));
        List<OrderItemSelection> items =
                IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> new OrderItemSelection("line-" + i, productId, List.of(), 1))
                        .toList();

        Order order = service.calculate(new CalculateOrderCommand(items));

        assertThat(order.items()).hasSize(100);
        assertThat(order.totalAmount().amount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("clientLineId 중복은 Catalog 조회 전에 INVALID_ORDER_ITEMS")
    void rejectsDuplicateClientLineIds() {
        List<OrderItemSelection> items =
                List.of(
                        new OrderItemSelection("line-1", UUID.randomUUID(), List.of(), 1),
                        new OrderItemSelection("line-1", UUID.randomUUID(), List.of(), 1));

        assertThatThrownBy(() -> service.calculate(new CalculateOrderCommand(items)))
                .isInstanceOf(InvalidOrderItemsException.class);
    }

    @Test
    @DisplayName("같은 Item의 Option ID 중복은 Catalog 조회 전에 INVALID_ORDER_ITEMS")
    void rejectsDuplicateOptionIds() {
        UUID optionId = UUID.randomUUID();
        OrderItemSelection item =
                new OrderItemSelection("line-1", UUID.randomUUID(), List.of(optionId, optionId), 1);

        assertThatThrownBy(() -> service.calculate(new CalculateOrderCommand(List.of(item))))
                .isInstanceOf(InvalidOrderItemsException.class);
    }

    @Test
    @DisplayName("null Option ID는 INVALID_ORDER_ITEMS")
    void rejectsNullOptionId() {
        assertThatThrownBy(
                        () ->
                                new OrderItemSelection(
                                        "line-1", UUID.randomUUID(), Collections.singletonList(null), 1))
                .isInstanceOf(InvalidOrderItemsException.class)
                .hasMessage("optionId는 필수입니다");
    }

    @Test
    @DisplayName("null Item은 INVALID_ORDER_ITEMS")
    void rejectsNullItem() {
        assertThatThrownBy(() -> new CalculateOrderCommand(Collections.singletonList(null)))
                .isInstanceOf(InvalidOrderItemsException.class)
                .hasMessage("주문 Item은 필수입니다");
    }

    @Test
    @DisplayName("clientLineId는 공백이 아니고 50자 이하여야 한다")
    void validatesClientLineId() {
        assertThatThrownBy(
                        () -> service.calculate(
                                new CalculateOrderCommand(
                                        List.of(new OrderItemSelection(" ", UUID.randomUUID(), List.of(), 1)))))
                .isInstanceOf(InvalidOrderItemsException.class);
        assertThatThrownBy(
                        () -> service.calculate(
                                new CalculateOrderCommand(
                                        List.of(new OrderItemSelection("x".repeat(51), UUID.randomUUID(), List.of(), 1)))))
                .isInstanceOf(InvalidOrderItemsException.class);
    }

    @Test
    @DisplayName("productId 누락은 Catalog 조회 전에 INVALID_ORDER_ITEMS")
    void rejectsMissingProductId() {
        OrderItemSelection item = new OrderItemSelection("line-1", null, List.of(), 1);

        assertThatThrownBy(() -> service.calculate(new CalculateOrderCommand(List.of(item))))
                .isInstanceOf(InvalidOrderItemsException.class);
    }
}
