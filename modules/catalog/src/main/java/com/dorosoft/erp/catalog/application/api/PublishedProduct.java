package com.dorosoft.erp.catalog.application.api;

import java.util.List;
import java.util.UUID;

/**
 * 공개 메뉴의 상품 Projection. 내부 version·stockManaged·Object Key·감사 정보는 담지 않는다.
 * orderable은 Catalog 상태(품절 여부)만 의미하며 재고 검증은 주문 확정 시점에 다시 한다.
 */
public record PublishedProduct(
        UUID productId,
        String name,
        String description,
        long basePrice,
        String imageUrl,
        String imageAltText,
        boolean soldOut,
        boolean orderable,
        List<PublishedOption> options) {}
