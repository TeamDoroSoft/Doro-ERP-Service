package com.dorosoft.erp.order.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.order.domain.item.InvalidOrderItemsException;
import com.dorosoft.erp.order.domain.item.InvalidQuantityException;
import com.dorosoft.erp.order.domain.money.OrderAmountOverflowException;
import com.dorosoft.erp.order.domain.order.EmptyOrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@DisplayName("OrderExceptionHandler - 06 공통 주문 관리 오류 코드 매핑")
class OrderExceptionHandlerTest {

    private final OrderExceptionHandler handler = new OrderExceptionHandler();

    @Test
    @DisplayName("빈 주문은 400 INVALID_ORDER_ITEMS")
    void mapsEmptyOrder() {
        assertProblem(
                handler.handleInvalidOrderItems(new EmptyOrderException()),
                HttpStatus.BAD_REQUEST,
                "INVALID_ORDER_ITEMS");
    }

    @Test
    @DisplayName("수량 범위 위반은 400 INVALID_ORDER_ITEMS")
    void mapsInvalidQuantity() {
        assertProblem(
                handler.handleInvalidOrderItems(new InvalidQuantityException(0)),
                HttpStatus.BAD_REQUEST,
                "INVALID_ORDER_ITEMS");
    }

    @Test
    @DisplayName("Item 구조·중복 위반은 400 INVALID_ORDER_ITEMS")
    void mapsInvalidOrderItems() {
        assertProblem(
                handler.handleInvalidOrderItems(new InvalidOrderItemsException("invalid items")),
                HttpStatus.BAD_REQUEST,
                "INVALID_ORDER_ITEMS");
    }

    @Test
    @DisplayName("금액 Overflow는 400 ORDER_AMOUNT_OVERFLOW")
    void mapsOrderAmountOverflow() {
        assertProblem(
                handler.handleOrderAmountOverflow(new OrderAmountOverflowException(new ArithmeticException())),
                HttpStatus.BAD_REQUEST,
                "ORDER_AMOUNT_OVERFLOW");
    }

    private static void assertProblem(ProblemDetail problem, HttpStatus status, String code) {
        assertThat(problem.getStatus()).isEqualTo(status.value());
        assertThat(problem.getTitle()).isEqualTo(code);
        assertThat(problem.getProperties()).containsEntry("code", code);
    }
}
