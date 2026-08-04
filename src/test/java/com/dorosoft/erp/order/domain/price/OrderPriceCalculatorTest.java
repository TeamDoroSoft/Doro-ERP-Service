package com.dorosoft.erp.order.domain.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.order.domain.money.OrderAmountOverflowException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrderPriceCalculator - unitPrice = baseUnitPrice + Σ additionalPrice, lineTotal = unitPrice × quantity")
class OrderPriceCalculatorTest {

    @Test
    @DisplayName("옵션이 없으면 unitPrice는 baseUnitPrice와 같다")
    void unitPriceWithoutOptions() {
        OrderPrice price = OrderPriceCalculator.calculate(4500L, List.of(), 2);

        assertThat(price.unitPrice().amount()).isEqualTo(4500L);
        assertThat(price.lineTotal().amount()).isEqualTo(9000L);
    }

    @Test
    @DisplayName("여러 옵션의 추가 금액을 모두 더해 unitPrice를 만든다")
    void unitPriceSumsAdditionalPrices() {
        OrderPrice price = OrderPriceCalculator.calculate(4500L, List.of(500L, 300L), 1);

        assertThat(price.unitPrice().amount()).isEqualTo(5300L);
        assertThat(price.lineTotal().amount()).isEqualTo(5300L);
    }

    @Test
    @DisplayName("lineTotal은 unitPrice에 quantity를 곱한 값이다")
    void lineTotalMultipliesByQuantity() {
        OrderPrice price = OrderPriceCalculator.calculate(4500L, List.of(500L), 3);

        assertThat(price.unitPrice().amount()).isEqualTo(5000L);
        assertThat(price.lineTotal().amount()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("0원 상품·옵션도 허용한다")
    void allowsZeroPrice() {
        OrderPrice price = OrderPriceCalculator.calculate(0L, List.of(0L), 1);

        assertThat(price.unitPrice().amount()).isZero();
        assertThat(price.lineTotal().amount()).isZero();
    }

    @Test
    @DisplayName("unitPrice 합산이 long 범위를 넘으면 ORDER_AMOUNT_OVERFLOW다")
    void unitPriceSumOverflows() {
        assertThatThrownBy(() -> OrderPriceCalculator.calculate(Long.MAX_VALUE, List.of(1L), 1))
                .isInstanceOf(OrderAmountOverflowException.class);
    }

    @Test
    @DisplayName("lineTotal 곱셈이 long 범위를 넘으면 ORDER_AMOUNT_OVERFLOW다")
    void lineTotalMultiplyOverflows() {
        assertThatThrownBy(() -> OrderPriceCalculator.calculate(Long.MAX_VALUE / 2, List.of(), 99))
                .isInstanceOf(OrderAmountOverflowException.class);
    }
}
