package com.dorosoft.erp.commerce.presentation.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommandUseCase;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.ChangeSoldOutCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogQueryUseCase;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import com.dorosoft.erp.commerce.presentation.catalog.CatalogRequests.ChangeSoldOutRequest;
import com.dorosoft.erp.commerce.presentation.catalog.CatalogRequests.CreateProductRequest;
import com.dorosoft.erp.commerce.presentation.catalog.CatalogRequests.UpdateProductRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 관리와 품절 변경 API (FR-CATALOG-002, FR-CATALOG-003).
 *
 * <p>기본 정보·가격 변경은 {@code OWNER}·{@code MANAGER}, 품절 변경은 직원 Role만 가능하며
 * {@code KIOSK_DEVICE}는 두 경우 모두 차단된다. 최종 검증은 Application이 수행한다.
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
public class CatalogProductController {

    private final CatalogCommandUseCase catalogCommandUseCase;
    private final CatalogQueryUseCase catalogQueryUseCase;

    public CatalogProductController(
            CatalogCommandUseCase catalogCommandUseCase, CatalogQueryUseCase catalogQueryUseCase) {
        this.catalogCommandUseCase = catalogCommandUseCase;
        this.catalogQueryUseCase = catalogQueryUseCase;
    }

    @GetMapping
    public ResponseEntity<List<ProductView>> products() {
        return ResponseEntity.ok(catalogQueryUseCase.loadManagedProducts());
    }

    @PostMapping
    public ResponseEntity<ProductView> create(@Valid @RequestBody CreateProductRequest request) {
        ProductView created = catalogCommandUseCase.createProduct(new CreateProductCommand(
                request.categoryId(),
                request.name(),
                request.description(),
                request.price(),
                request.displayOrder(),
                request.active()));
        return ResponseEntity
                .created(URI.create("/api/v1/catalog/products/" + created.productId()))
                .eTag("\"" + created.version() + "\"")
                .body(created);
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<ProductView> update(
            @PathVariable UUID productId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductView updated = catalogCommandUseCase.updateProduct(
                productId,
                IfMatchVersion.parse(ifMatch),
                new UpdateProductCommand(
                        request.categoryId(),
                        request.name(),
                        request.description(),
                        request.price(),
                        request.displayOrder(),
                        request.active()));
        return ResponseEntity.ok().eTag("\"" + updated.version() + "\"").body(updated);
    }

    @PatchMapping("/{productId}/sold-out")
    public ResponseEntity<ProductView> changeSoldOut(
            @PathVariable UUID productId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ChangeSoldOutRequest request) {
        ProductView updated = catalogCommandUseCase.changeSoldOut(
                productId, IfMatchVersion.parse(ifMatch), new ChangeSoldOutCommand(request.soldOut()));
        return ResponseEntity.ok().eTag("\"" + updated.version() + "\"").body(updated);
    }
}
