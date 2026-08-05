package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.application.query.ProductListPage;
import java.util.List;

public record ProductListResponse(List<ProductResponse> items, String nextCursor, boolean hasMore) {

    public static ProductListResponse from(ProductListPage page) {
        return new ProductListResponse(
                page.items().stream().map(ProductResponse::from).toList(), page.nextCursor(), page.hasMore());
    }
}
