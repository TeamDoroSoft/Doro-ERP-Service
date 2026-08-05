package com.dorosoft.erp.catalog.presentation.query;

import com.dorosoft.erp.catalog.application.query.CatalogOverviewQueryService;
import com.dorosoft.erp.catalog.application.query.ProductDetailQueryService;
import com.dorosoft.erp.catalog.application.query.ProductListQueryService;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.presentation.common.CatalogApiEnvelope;
import com.dorosoft.erp.catalog.presentation.common.CatalogRequestId;
import com.dorosoft.erp.catalog.presentation.dto.CatalogOverviewResponse;
import com.dorosoft.erp.catalog.presentation.dto.ProductListResponse;
import com.dorosoft.erp.catalog.presentation.dto.ProductResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogQueryController {

    private final CatalogOverviewQueryService catalogOverviewQueryService;
    private final ProductListQueryService productListQueryService;
    private final ProductDetailQueryService productDetailQueryService;

    public CatalogQueryController(
            CatalogOverviewQueryService catalogOverviewQueryService,
            ProductListQueryService productListQueryService,
            ProductDetailQueryService productDetailQueryService) {
        this.catalogOverviewQueryService = catalogOverviewQueryService;
        this.productListQueryService = productListQueryService;
        this.productDetailQueryService = productDetailQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('catalog.read')")
    public CatalogApiEnvelope<CatalogOverviewResponse> getOverview(HttpServletRequest request) {
        var overview = catalogOverviewQueryService.getOverview();
        return CatalogApiEnvelope.of(CatalogOverviewResponse.from(overview), CatalogRequestId.from(request));
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('catalog.read')")
    public CatalogApiEnvelope<ProductListResponse> listProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean salesEnabled,
            @RequestParam(required = false) Boolean soldOut,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        var page = productListQueryService.list(categoryId, salesEnabled, soldOut, cursor, limit);
        return CatalogApiEnvelope.of(ProductListResponse.from(page), CatalogRequestId.from(request));
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAuthority('catalog.read')")
    public CatalogApiEnvelope<ProductResponse> getProduct(@PathVariable UUID productId, HttpServletRequest request) {
        Product product = productDetailQueryService.findById(productId);
        return CatalogApiEnvelope.of(ProductResponse.from(product), CatalogRequestId.from(request));
    }
}
