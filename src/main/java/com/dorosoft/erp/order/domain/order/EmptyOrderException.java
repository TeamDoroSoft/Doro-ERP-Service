package com.dorosoft.erp.order.domain.order;

/** Item이 하나도 없는 주문 생성 시도. Order API에서는 400 INVALID_ORDER_ITEMS로 매핑한다. */
public class EmptyOrderException extends RuntimeException {

    public EmptyOrderException() {
        super("주문 항목이 최소 1개 이상이어야 합니다");
    }
}
