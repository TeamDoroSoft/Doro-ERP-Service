package com.dorosoft.erp.commerce.presentation.catalog;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommandUseCase;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogQueryUseCase;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.presentation.catalog.CatalogRequests.CreateCategoryRequest;
import com.dorosoft.erp.commerce.presentation.catalog.CatalogRequests.UpdateCategoryRequest;
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
 * Category 관리 API (FR-CATALOG-001). 권한은 Application이 최종 검증한다.
 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
public class CatalogCategoryController {

    private final CatalogCommandUseCase catalogCommandUseCase;
    private final CatalogQueryUseCase catalogQueryUseCase;

    public CatalogCategoryController(
            CatalogCommandUseCase catalogCommandUseCase, CatalogQueryUseCase catalogQueryUseCase) {
        this.catalogCommandUseCase = catalogCommandUseCase;
        this.catalogQueryUseCase = catalogQueryUseCase;
    }

    @GetMapping
    public ResponseEntity<List<CategoryView>> categories() {
        return ResponseEntity.ok(catalogQueryUseCase.loadManagedCategories());
    }

    @PostMapping
    public ResponseEntity<CategoryView> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryView created = catalogCommandUseCase.createCategory(
                new CreateCategoryCommand(request.name(), request.displayOrder(), request.active()));
        return ResponseEntity
                .created(URI.create("/api/v1/catalog/categories/" + created.categoryId()))
                .eTag("\"" + created.version() + "\"")
                .body(created);
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryView> update(
            @PathVariable UUID categoryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryView updated = catalogCommandUseCase.updateCategory(
                categoryId,
                IfMatchVersion.parse(ifMatch),
                new UpdateCategoryCommand(request.name(), request.displayOrder(), request.active()));
        return ResponseEntity.ok().eTag("\"" + updated.version() + "\"").body(updated);
    }
}
