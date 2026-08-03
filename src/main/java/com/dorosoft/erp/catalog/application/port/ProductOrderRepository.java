package com.dorosoft.erp.catalog.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Category 안의 Product 정렬(FR-MENU-008) 전용 좁은 Port다. Product 생성·가격 등 전체 Aggregate는
 * MENU-04가 소유하므로, 이 Port는 product_id·category_id·display_order 세 컬럼만 다룬다.
 */
public interface ProductOrderRepository {

    /** display_order 오름차순으로 해당 Category에 속한 Product ID를 반환한다. */
    List<UUID> findProductIdsByCategory(UUID categoryId);

    /** 해당 Category 안의 Product 순서를 0부터 다시 부여한다. 대상 집합 검증은 호출자가 미리 마친다. */
    void replaceDisplayOrder(UUID categoryId, List<UUID> orderedProductIds);
}
