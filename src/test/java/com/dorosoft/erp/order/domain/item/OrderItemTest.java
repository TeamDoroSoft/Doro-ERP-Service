package com.dorosoft.erp.order.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrderItem - 실제 Order Item 수량은 1~99만 허용한다")
class OrderItemTest {

    private static OrderItem createWithQuantity(int quantity) {
        return OrderItem.create("line-1", UUID.randomUUID(), "아메리카노", 4500L, List.of(), quantity, false, 0L);
    }

    @Test
    @DisplayName("수량 0은 INVALID_QUANTITY(Backend로 quantity=0 전달)")
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> createWithQuantity(0)).isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    @DisplayName("수량 100은 INVALID_QUANTITY(상한 초과)")
    void rejectsAboveMax() {
        assertThatThrownBy(() -> createWithQuantity(100)).isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    @DisplayName("수량 음수는 INVALID_QUANTITY")
    void rejectsNegativeQuantity() {
        assertThatThrownBy(() -> createWithQuantity(-1)).isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    @DisplayName("경계값 1과 99는 통과한다")
    void acceptsBoundaryQuantities() {
        assertThatCode(() -> createWithQuantity(1)).doesNotThrowAnyException();
        assertThatCode(() -> createWithQuantity(99)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("옵션 추가 금액을 포함해 unitPrice·lineTotal을 계산한다")
    void computesPriceWithOptions() {
        UUID optionId = UUID.randomUUID();
        OrderItem item =
                OrderItem.create(
                        "line-1",
                        UUID.randomUUID(),
                        "아메리카노",
                        4500L,
                        List.of(new OrderItemOption(optionId, "샷 추가", 500L)),
                        2,
                        false,
                        3L);

        assertThat(item.price().unitPrice().amount()).isEqualTo(5000L);
        assertThat(item.price().lineTotal().amount()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("restore()는 저장된 주문 시점 금액을 다시 계산하지 않는다")
    void restoresStoredSnapshotAmounts() {
        OrderItem item =
                OrderItem.restore(
                        "line-1", UUID.randomUUID(), "아메리카노", 4500L, List.of(), 2, 7000L, 14000L, false, 3L);

        assertThat(item.price().unitPrice().amount()).isEqualTo(7000L);
        assertThat(item.price().lineTotal().amount()).isEqualTo(14000L);
    }

    @Test
    @DisplayName("같은 Item의 Option ID 중복을 거부한다")
    void rejectsDuplicateOptionIds() {
        UUID optionId = UUID.randomUUID();
        List<OrderItemOption> options =
                List.of(
                        new OrderItemOption(optionId, "샷 추가", 500L),
                        new OrderItemOption(optionId, "샷 추가", 500L));

        assertThatThrownBy(
                        () -> OrderItem.create(
                                "line-1", UUID.randomUUID(), "아메리카노", 4500L, options, 1, false, 0L))
                .isInstanceOf(InvalidOrderItemsException.class);
    }
}
