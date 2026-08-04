package com.dorosoft.erp.order.domain.price;

import com.dorosoft.erp.order.domain.money.OrderAmount;

/** 한 주문 항목의 옵션 합계, 단가와 줄 합계(06 공통 주문 관리 금액 공식). */
public record OrderPrice(OrderAmount optionUnitAmount, OrderAmount unitPrice, OrderAmount lineTotal) {}
