package com.dorosoft.erp.order.domain.item;

/** Order Item 수량이 1~99 범위를 벗어남(0 포함). Order API에서는 400 INVALID_ORDER_ITEMS로 매핑한다. */
public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(int quantity) {
        super("quantity는 " + OrderItem.MIN_QUANTITY + "~" + OrderItem.MAX_QUANTITY + " 사이의 정수여야 합니다. 입력값=" + quantity);
    }
}
