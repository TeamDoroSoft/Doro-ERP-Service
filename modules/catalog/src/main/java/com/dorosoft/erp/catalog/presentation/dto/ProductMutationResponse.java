package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.domain.product.Product;

/** 변경 응답에는 최신 catalogRevision을 포함한다(API 명세 공통 계약). */
public record ProductMutationResponse(ProductResponse product, long catalogRevision) {

    public static ProductMutationResponse of(Product product, long catalogRevision) {
        return new ProductMutationResponse(ProductResponse.from(product), catalogRevision);
    }
}
