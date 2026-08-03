package com.dorosoft.erp.catalog.domain.product;

/** 옵션 ID 중복, 다른 Product 소속·존재하지 않는 옵션 ID 등 구조 규칙 위반. API 오류 코드: 400 INVALID_PRODUCT_OPTIONS. */
public class InvalidProductOptionsException extends RuntimeException {

    public InvalidProductOptionsException(String reason) {
        super(reason);
    }
}
