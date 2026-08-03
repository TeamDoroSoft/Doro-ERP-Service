package com.dorosoft.erp.catalog.domain.orderability;

/** 주문 항목 검증 실패 사유(판매 가능성 판정 명세). 판정 우선순위는 선언 순서와 같다. */
public enum OrderabilityReason {
    PRODUCT_NOT_FOR_SALE,
    PRODUCT_SOLD_OUT,
    DUPLICATE_OPTION_SELECTION,
    OPTION_NOT_FOUND,
    OPTION_NOT_ORDERABLE
}
