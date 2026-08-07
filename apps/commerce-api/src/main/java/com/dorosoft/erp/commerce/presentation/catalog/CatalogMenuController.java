package com.dorosoft.erp.commerce.presentation.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogQueryUseCase;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.SalesMenuView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POS·Kiosk 공통 판매 메뉴 조회 (FR-CATALOG-004).
 *
 * <p>접근: {@code OWNER}, {@code MANAGER}, {@code STAFF}, {@code KIOSK_DEVICE}.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogMenuController {

    private final CatalogQueryUseCase catalogQueryUseCase;

    public CatalogMenuController(CatalogQueryUseCase catalogQueryUseCase) {
        this.catalogQueryUseCase = catalogQueryUseCase;
    }

    @GetMapping("/menu")
    public ResponseEntity<SalesMenuView> menu() {
        return ResponseEntity.ok(catalogQueryUseCase.loadSalesMenu());
    }
}
