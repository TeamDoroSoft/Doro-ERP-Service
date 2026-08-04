package com.dorosoft.erp.order.domain.item;

import com.dorosoft.erp.order.domain.money.OrderAmount;
import com.dorosoft.erp.order.domain.price.OrderPrice;
import com.dorosoft.erp.order.domain.price.OrderPriceCalculator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 주문 시점 상품명·가격·옵션·수량·금액 Snapshot(06 공통 주문 관리 Catalog Snapshot). 화면의 수량 0(미선택)은
 * Item 자체를 만들지 않는 방식으로 걸러지며, 실제 Order Item 수량은 1~99만 허용한다.
 */
public final class OrderItem {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 99;
    public static final int MAX_CLIENT_LINE_ID_LENGTH = 50;

    private final UUID lineId;
    private final String clientLineId;
    private final UUID productId;
    private final String productName;
    private final long baseUnitPrice;
    private final List<OrderItemOption> options;
    private final int quantity;
    private final OrderPrice price;
    private final boolean stockManaged;
    private final long catalogRevision;

    private OrderItem(
            UUID lineId,
            String clientLineId,
            UUID productId,
            String productName,
            long baseUnitPrice,
            List<OrderItemOption> options,
            int quantity,
            OrderPrice price,
            boolean stockManaged,
            long catalogRevision) {
        this.lineId = Objects.requireNonNull(lineId, "lineId는 필수다");
        if (clientLineId == null || clientLineId.isBlank() || clientLineId.length() > MAX_CLIENT_LINE_ID_LENGTH) {
            throw new InvalidOrderItemsException(
                    "clientLineId는 1~" + MAX_CLIENT_LINE_ID_LENGTH + "자의 식별자여야 합니다");
        }
        this.clientLineId = clientLineId;
        this.productId = Objects.requireNonNull(productId, "productId는 필수다");
        this.productName = Objects.requireNonNull(productName, "productName은 필수다");
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
        this.baseUnitPrice = baseUnitPrice;
        this.options = List.copyOf(Objects.requireNonNull(options, "options는 필수다"));
        if (this.options.stream().map(OrderItemOption::optionId).anyMatch(Objects::isNull)
                || new HashSet<>(this.options.stream().map(OrderItemOption::optionId).toList()).size()
                        != this.options.size()) {
            throw new InvalidOrderItemsException("같은 주문 항목에서 optionId를 중복 선택할 수 없습니다");
        }
        this.quantity = quantity;
        this.price = Objects.requireNonNull(price, "price는 필수다");
        this.stockManaged = stockManaged;
        this.catalogRevision = catalogRevision;
    }

    /** baseUnitPrice·옵션·수량으로 OrderPriceCalculator를 호출해 unitPrice·lineTotal을 계산한다. */
    public static OrderItem create(
            String clientLineId,
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
                UUID.randomUUID(),
                clientLineId,
                productId,
                productName,
                baseUnitPrice,
                options,
                quantity,
                price,
                stockManaged,
                catalogRevision);
    }

    /** DB에 저장된 주문 시점 단가와 줄 합계를 현재 계산식으로 다시 계산하지 않고 복원한다. */
    public static OrderItem restore(
            UUID lineId,
            String clientLineId,
            UUID productId,
            String productName,
            long baseUnitPrice,
            List<OrderItemOption> options,
            int quantity,
            long optionUnitAmount,
            long unitPrice,
            long lineTotal,
            boolean stockManaged,
            long catalogRevision) {
        OrderPrice storedPrice =
                new OrderPrice(
                        OrderAmount.of(optionUnitAmount), OrderAmount.of(unitPrice), OrderAmount.of(lineTotal));
        return new OrderItem(
                lineId,
                clientLineId,
                productId,
                productName,
                baseUnitPrice,
                options,
                quantity,
                storedPrice,
                stockManaged,
                catalogRevision);
    }

    public UUID lineId() {
        return lineId;
    }

    public String clientLineId() {
        return clientLineId;
    }

    public UUID productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public long baseUnitPrice() {
        return baseUnitPrice;
    }

    public List<OrderItemOption> options() {
        return options;
    }

    public int quantity() {
        return quantity;
    }

    public OrderPrice price() {
        return price;
    }

    public boolean stockManaged() {
        return stockManaged;
    }

    public long catalogRevision() {
        return catalogRevision;
    }
}
