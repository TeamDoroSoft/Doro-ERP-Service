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

    /** 판매 메뉴 항목. 비활성 Category·비활성 상품은 목록에 포함하지 않는다. */
    public record SalesMenuItemView(
            UUID productId,
            String name,
            String description,
            long price,
            boolean soldOut,
            boolean sellable,
            int displayOrder,
            long version) {
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
