package com.dorosoft.erp.order.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrderAmount - 확정된 add()·multiply() 정책")
class OrderAmountTest {

    @Test
    @DisplayName("0원은 허용한다")
    void allowsZero() {
        assertThatCode(() -> OrderAmount.of(0L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("음수는 거부한다")
    void rejectsNegative() {
        assertThatThrownBy(() -> OrderAmount.of(-1L)).isInstanceOf(NegativeOrderAmountException.class);
    }

    @Test
    @DisplayName("add()는 Math.addExact로 두 금액을 더한다")
    void addsAmounts() {
        OrderAmount result = OrderAmount.of(4500L).add(OrderAmount.of(500L));

        assertThat(result.amount()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("add()가 long 범위를 넘으면 ORDER_AMOUNT_OVERFLOW다")
    void addOverflows() {
        OrderAmount max = OrderAmount.of(Long.MAX_VALUE);

        assertThatThrownBy(() -> max.add(OrderAmount.of(1L))).isInstanceOf(OrderAmountOverflowException.class);
    }

    @Test
    @DisplayName("multiply()는 Math.multiplyExact로 수량을 곱한다")
    void multipliesByQuantity() {
        OrderAmount result = OrderAmount.of(5000L).multiply(2);

        assertThat(result.amount()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("multiply()가 long 범위를 넘으면 ORDER_AMOUNT_OVERFLOW다")
    void multiplyOverflows() {
        OrderAmount huge = OrderAmount.of(Long.MAX_VALUE / 2);

        assertThatThrownBy(() -> huge.multiply(99)).isInstanceOf(OrderAmountOverflowException.class);
    }

    @Test
    @DisplayName("multiply()는 0과 음수 계수를 거부한다")
    void rejectsNonPositiveMultiplier() {
        OrderAmount amount = OrderAmount.of(5000L);

        assertThatThrownBy(() -> amount.multiply(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> amount.multiply(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
