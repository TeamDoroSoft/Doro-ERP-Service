package com.dorosoft.erp.order.domain.order;

/** Item이 하나도 없는 주문 생성 시도. API 오류 코드: 400 EMPTY_ORDER. */
public class EmptyOrderException extends RuntimeException {

    public EmptyOrderException() {
        super("주문 항목이 최소 1개 이상이어야 합니다");
    }
}
