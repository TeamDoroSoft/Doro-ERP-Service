package com.dorosoft.erp.commerce.application.api.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.SalesMenuView;
import java.util.List;

public interface CatalogQueryUseCase {

    /** POS·Kiosk 공통 판매 메뉴. 활성 Category의 활성 상품만 반환한다 (FR-CATALOG-004). */
    SalesMenuView loadSalesMenu();

    /** 관리 화면용 Category 목록. 비활성 Category도 포함한다. */
    List<CategoryView> loadManagedCategories();

    /** 관리 화면용 상품 목록. 비활성 상품도 포함한다. */
    List<ProductView> loadManagedProducts();
}
