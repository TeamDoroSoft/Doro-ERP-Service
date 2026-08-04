package com.dorosoft.erp.order.presentation;

import com.dorosoft.erp.order.domain.item.InvalidQuantityException;
import com.dorosoft.erp.order.domain.money.OrderAmountOverflowException;
import com.dorosoft.erp.order.domain.order.EmptyOrderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 06 공통 주문 관리 API 명세의 안정적인 오류 코드로 Order 도메인 예외를 변환한다. */
@RestControllerAdvice
public class OrderExceptionHandler {

    private static final String INVALID_ORDER_ITEMS = "INVALID_ORDER_ITEMS";
    private static final String ORDER_AMOUNT_OVERFLOW = "ORDER_AMOUNT_OVERFLOW";

    @ExceptionHandler({EmptyOrderException.class, InvalidQuantityException.class})
    public ProblemDetail handleInvalidOrderItems(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, INVALID_ORDER_ITEMS, exception.getMessage());
    }

    @ExceptionHandler(OrderAmountOverflowException.class)
    public ProblemDetail handleOrderAmountOverflow(OrderAmountOverflowException exception) {
        return problem(HttpStatus.BAD_REQUEST, ORDER_AMOUNT_OVERFLOW, exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code);
        problem.setProperty("code", code);
        return problem;
    }
}
