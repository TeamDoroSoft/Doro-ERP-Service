package com.dorosoft.erp.catalog.domain.product;

/** 상품·옵션 금액이 음수다. API 오류 코드: 400 INVALID_PRICE. */
public class InvalidPriceException extends RuntimeException {

    public InvalidPriceException(String fieldName, long value) {
        super(fieldName + "은(는) 0 이상의 정수 원화여야 합니다. 입력값=" + value);
    }
}
