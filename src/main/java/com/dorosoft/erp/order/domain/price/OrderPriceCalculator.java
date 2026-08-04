package com.dorosoft.erp.order.domain.price;

import com.dorosoft.erp.order.domain.money.OrderAmount;
import java.util.List;

/**
 * 확정된 add()·multiply() 정책으로 한 주문 항목의 금액을 계산한다(06 공통 주문 관리 금액 공식).
 *
 * <pre>
 * unitPrice = baseUnitPrice + Σ additionalPrice
 * lineTotal = unitPrice × quantity
 * </pre>
 */
public final class OrderPriceCalculator {

    private OrderPriceCalculator() {}

    public static OrderPrice calculate(long baseUnitPrice, List<Long> additionalPrices, int quantity) {
        OrderAmount unitPrice = OrderAmount.of(baseUnitPrice);
        for (long additionalPrice : additionalPrices) {
            unitPrice = unitPrice.add(OrderAmount.of(additionalPrice));
        }
        OrderAmount lineTotal = unitPrice.multiply(quantity);
        return new OrderPrice(unitPrice, lineTotal);
    }
}
