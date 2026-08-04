package com.dorosoft.erp.order.domain.item;

import com.dorosoft.erp.order.domain.price.OrderPrice;
import com.dorosoft.erp.order.domain.price.OrderPriceCalculator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 주문 시점 상품명·가격·옵션·수량·금액 Snapshot(06 공통 주문 관리 Catalog Snapshot). 화면의 수량 0(미선택)은
 * Item 자체를 만들지 않는 방식으로 걸러지며, 실제 Order Item 수량은 1~99만 허용한다.
 */
public record OrderItem(
        UUID productId,
        String productName,
        long baseUnitPrice,
        List<OrderItemOption> options,
        int quantity,
        OrderPrice price,
        boolean stockManaged,
        long catalogRevision) {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 99;

    public OrderItem {
        Objects.requireNonNull(productId, "productId는 필수다");
        Objects.requireNonNull(productName, "productName은 필수다");
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
        options = List.copyOf(options);
    }

    /** baseUnitPrice·옵션·수량으로 OrderPriceCalculator를 호출해 unitPrice·lineTotal을 계산한다. */
    public static OrderItem create(
            UUID productId,
            String productName,
            long baseUnitPrice,
            List<OrderItemOption> options,
            int quantity,
            boolean stockManaged,
            long catalogRevision) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
        List<Long> additionalPrices = options.stream().map(OrderItemOption::additionalPrice).toList();
        OrderPrice price = OrderPriceCalculator.calculate(baseUnitPrice, additionalPrices, quantity);
        return new OrderItem(
                productId, productName, baseUnitPrice, options, quantity, price, stockManaged, catalogRevision);
    }
}
