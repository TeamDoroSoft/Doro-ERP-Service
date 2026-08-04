package com.dorosoft.erp.order.domain.money;

/** OrderAmount의 checked add·multiply가 long 범위를 초과함. Order API에서는 400 ORDER_AMOUNT_OVERFLOW로 매핑한다. */
public class OrderAmountOverflowException extends RuntimeException {

    public OrderAmountOverflowException(ArithmeticException cause) {
        super("주문 금액 계산이 long 범위를 초과했습니다", cause);
    }
}
