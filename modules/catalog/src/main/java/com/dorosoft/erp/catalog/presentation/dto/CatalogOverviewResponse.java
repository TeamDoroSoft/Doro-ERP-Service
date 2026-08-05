package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.application.query.CatalogOverview;
import com.dorosoft.erp.catalog.application.query.CategoryOverview;
import java.util.List;
import java.util.UUID;

public record CatalogOverviewResponse(long catalogRevision, List<CategoryOverviewResponse> categories) {

    public static CatalogOverviewResponse from(CatalogOverview overview) {
        return new CatalogOverviewResponse(
                overview.catalogRevision(), overview.categories().stream().map(CategoryOverviewResponse::from).toList());
    }

    public record CategoryOverviewResponse(
            UUID categoryId, String name, int displayOrder, long version, List<ProductResponse> products) {

        static CategoryOverviewResponse from(CategoryOverview overview) {
            return new CategoryOverviewResponse(
                    overview.category().categoryId(),
                    overview.category().name(),
                    overview.category().displayOrder(),
                    overview.category().version(),
                    overview.products().stream().map(ProductResponse::from).toList());
        }
    }
}
