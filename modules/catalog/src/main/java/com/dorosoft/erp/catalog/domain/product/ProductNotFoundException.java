package com.dorosoft.erp.catalog.domain.product;

import java.util.UUID;

/** 현재 업체에 존재하지 않는 productId 요청. API 오류 코드: 404 PRODUCT_NOT_FOUND. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("Product를 찾을 수 없습니다. productId=" + productId);
    }
}
