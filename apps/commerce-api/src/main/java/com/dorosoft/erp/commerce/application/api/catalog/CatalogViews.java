package com.dorosoft.erp.commerce.application.api.catalog;

import java.util.List;
import java.util.UUID;

/**
 * Catalog Use Case 출력 계약. Presentation은 Domain 타입에 의존하지 않는다.
 */
public final class CatalogViews {

    private CatalogViews() {
    }

    public record CategoryView(
            UUID categoryId,
            String name,
            int displayOrder,
            boolean active,
            long version) {
    }

    public record ProductView(
            UUID productId,
            UUID categoryId,
            String name,
            String description,
            long price,
            boolean soldOut,
            boolean active,
            int displayOrder,
            long version) {
    }

    /**
     * 판매 메뉴 항목 (FR-CATALOG-004).
     *
     * <p>비활성 Category·비활성 상품·품절 상품은 애초에 목록에 포함되지 않으므로
     * 판매 가능 여부를 나타내는 Flag를 따로 두지 않는다. 목록에 있으면 곧 판매 가능이다.
     * 품절 상태를 함께 봐야 하는 직원 운영 화면은 관리용 조회 계약을 사용한다.
     */
    public record SalesMenuItemView(
            UUID productId,
            String name,
            String description,
            long price,
            int displayOrder) {
    }

    public record SalesMenuCategoryView(
            UUID categoryId,
            String name,
            int displayOrder,
            List<SalesMenuItemView> products) {
    }

    public record SalesMenuView(String currency, List<SalesMenuCategoryView> categories) {
    }
}
