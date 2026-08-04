package com.dorosoft.erp.catalog.domain.orderability;

import java.util.UUID;

/**
 * 주문 항목이 판매 가능성 판정을 통과하지 못했다(판매 가능성 판정 명세의 판정 우선순위 1~6번).
 * API 오류 코드 매핑: PRODUCT_NOT_FOUND -> 404 PRODUCT_NOT_FOUND,
 * DUPLICATE_OPTION_SELECTION -> 400 INVALID_ORDER_ITEMS(Order가 방어적으로도 매핑),
 * PRODUCT_NOT_FOR_SALE -> 409 PRODUCT_NOT_FOR_SALE, PRODUCT_SOLD_OUT -> 409 PRODUCT_SOLD_OUT,
 * OPTION_NOT_FOUND·OPTION_NOT_ORDERABLE -> 409 OPTION_NOT_ORDERABLE.
 */
public class OrderabilityRejectedException extends RuntimeException {

    private final OrderabilityReason reason;
    private final UUID productId;

    public OrderabilityRejectedException(OrderabilityReason reason, UUID productId) {
        super("주문 판매 가능성 판정에 실패했습니다. reason=" + reason + ", productId=" + productId);
        this.reason = reason;
        this.productId = productId;
    }

    public OrderabilityReason reason() {
        return reason;
    }

    public UUID productId() {
        return productId;
    }
}
