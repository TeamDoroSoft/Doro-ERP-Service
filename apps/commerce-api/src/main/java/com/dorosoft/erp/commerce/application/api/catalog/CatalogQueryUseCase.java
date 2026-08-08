package com.dorosoft.erp.commerce.application.api.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.SalesMenuView;
import java.util.List;

public interface CatalogQueryUseCase {

    /**
     * POS·Kiosk 공통 판매 메뉴 (FR-CATALOG-004).
     *
     * <p>활성 Category의 활성·비품절 상품만 반환한다. 채널이나 Role에 따른 분기가 없으므로
     * 같은 Tenant에서 같은 시점에 조회하면 POS와 Kiosk가 완전히 같은 결과를 받는다.
     */
    SalesMenuView loadSalesMenu();

    /** 운영·관리 화면용 Category 목록. 비활성 Category도 포함한다. */
    List<CategoryView> loadManagedCategories();

    /** 운영·관리 화면용 상품 목록. 비활성·품절 상품도 포함한다. */
    List<ProductView> loadManagedProducts();
}
