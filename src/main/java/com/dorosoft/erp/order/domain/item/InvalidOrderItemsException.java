package com.dorosoft.erp.order.domain.item;

/** Item 수·clientLineId·Option ID 형식 또는 중복이 Order 계약을 위반함. */
public class InvalidOrderItemsException extends RuntimeException {

    public InvalidOrderItemsException(String message) {
        super(message);
    }
}
