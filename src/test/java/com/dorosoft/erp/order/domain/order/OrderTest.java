package com.dorosoft.erp.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.order.domain.item.OrderItem;
import com.dorosoft.erp.order.domain.money.OrderAmountOverflowException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Order - Item이 없으면 EMPTY_ORDER, 있으면 lineTotal 합계를 totalAmount로 갖는다")
class OrderTest {

    private static OrderItem item(long baseUnitPrice, int quantity) {
        return OrderItem.create(UUID.randomUUID(), "아메리카노", baseUnitPrice, List.of(), quantity, false, 0L);
    }

    @Test
    @DisplayName("Item이 없으면 EMPTY_ORDER")
    void rejectsEmptyItems() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), List.of(), Instant.now()))
                .isInstanceOf(EmptyOrderException.class);
    }

    @Test
    @DisplayName("totalAmount는 모든 Item의 lineTotal 합이다")
    void sumsLineTotalsIntoTotalAmount() {
        OrderItem first = item(4500L, 2); // 9000
        OrderItem second = item(3000L, 1); // 3000

        Order order = Order.create(UUID.randomUUID(), List.of(first, second), Instant.now());

        assertThat(order.totalAmount().amount()).isEqualTo(12000L);
    }

    @Test
    @DisplayName("합산이 long 범위를 넘으면 ORDER_AMOUNT_OVERFLOW")
    void overflowsWhenSummingItems() {
        OrderItem first = item(Long.MAX_VALUE, 1);
        OrderItem second = item(1L, 1);

        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), List.of(first, second), Instant.now()))
                .isInstanceOf(OrderAmountOverflowException.class);
    }

    @Test
    @DisplayName("restore()는 저장된 주문 총액을 다시 계산하지 않는다")
    void restoresStoredTotalAmount() {
        Order order = Order.restore(UUID.randomUUID(), List.of(item(4500L, 2)), 12000L, Instant.now());

        assertThat(order.totalAmount().amount()).isEqualTo(12000L);
    }
}
