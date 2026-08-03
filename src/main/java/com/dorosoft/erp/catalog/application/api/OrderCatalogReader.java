package com.dorosoft.erp.catalog.application.api;

import java.util.List;
import java.util.UUID;

/**
 * Order가 주문 생성 시 상품·옵션을 검증하고 서버 가격 Snapshot을 받는 공개 계약(기능 연계 명세).
 * 재고 수량은 조회하지 않으며 stockManaged 표시만 전달한다 — 재고 검증·차감은 5.6·5.14 소유다.
 */
public interface OrderCatalogReader {

    /**
     * 상품 미존재는 ProductNotFoundException(404), 판매 비활성·품절·옵션 문제는
     * OrderabilityRejectedException을 던진다. 옵션을 선택하지 않는 것은 허용한다.
     */
    OrderCatalogSnapshot resolveForOrder(UUID productId, List<UUID> selectedOptionIds);
}
