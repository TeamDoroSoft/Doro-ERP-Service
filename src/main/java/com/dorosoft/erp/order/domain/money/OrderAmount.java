package com.dorosoft.erp.order.domain.money;

/**
 * 정수 원화 금액 값 객체(확정된 add()·multiply() 정책). 0원은 허용하고 음수는 금지한다.
 * add·multiply는 각각 {@link Math#addExact(long, long)}, {@link Math#multiplyExact(long, long)}로
 * 계산해 long 범위를 넘으면 {@link OrderAmountOverflowException}을 던진다.
 */
public record OrderAmount(long amount) {

    public OrderAmount {
        if (amount < 0) {
            throw new NegativeOrderAmountException(amount);
        }
    }

    public static OrderAmount of(long amount) {
        return new OrderAmount(amount);
    }

    public OrderAmount add(OrderAmount other) {
        try {
            return new OrderAmount(Math.addExact(amount, other.amount));
        } catch (ArithmeticException e) {
            throw new OrderAmountOverflowException(e);
        }
    }

    public OrderAmount multiply(long factor) {
        try {
            return new OrderAmount(Math.multiplyExact(amount, factor));
        } catch (ArithmeticException e) {
            throw new OrderAmountOverflowException(e);
        }
    }
}
