package com.dorosoft.erp.order.application.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.catalog.application.api.OrderCatalogSnapshot;
import com.dorosoft.erp.catalog.application.api.SelectedOptionSnapshot;
import com.dorosoft.erp.order.domain.item.InvalidQuantityException;
import com.dorosoft.erp.order.domain.order.EmptyOrderException;
import com.dorosoft.erp.order.domain.order.Order;
import java.util.List;
import java.util.UUID;
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
                new CalculateOrderCommand(List.of(new OrderItemSelection(unregisteredProductId, List.of(), 0)));

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
                new CalculateOrderCommand(List.of(new OrderItemSelection(productId, List.of(optionId), 2)));

        Order order = service.calculate(command);

        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).price().unitPrice().amount()).isEqualTo(5000L);
        assertThat(order.items().get(0).price().lineTotal().amount()).isEqualTo(10000L);
        assertThat(order.totalAmount().amount()).isEqualTo(10000L);
        assertThat(orderRepository.findById(order.orderId())).contains(order);
    }
}
