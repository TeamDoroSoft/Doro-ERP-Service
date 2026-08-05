package com.dorosoft.erp.catalog.presentation.category;

import com.dorosoft.erp.catalog.application.category.CreateCategoryService;
import com.dorosoft.erp.catalog.application.category.ReplaceCategoryOrderService;
import com.dorosoft.erp.catalog.application.category.UpdateCategoryService;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.query.CatalogRevisionQueryService;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import com.dorosoft.erp.catalog.presentation.common.CatalogApiEnvelope;
import com.dorosoft.erp.catalog.presentation.common.CatalogIfMatch;
import com.dorosoft.erp.catalog.presentation.common.CatalogRequestId;
import com.dorosoft.erp.catalog.presentation.common.CatalogWebContextFactory;
import com.dorosoft.erp.catalog.presentation.dto.CatalogRevisionResponse;
import com.dorosoft.erp.catalog.presentation.dto.CategoryMutationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/categories")
public class CategoryController {
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final CreateCategoryService createCategoryService;
    private final UpdateCategoryService updateCategoryService;
    private final ReplaceCategoryOrderService replaceCategoryOrderService;
    private final CatalogRevisionQueryService catalogRevisionQueryService;
    private final CatalogWebContextFactory webContextFactory;

    public CategoryController(
            CreateCategoryService createCategoryService,
            UpdateCategoryService updateCategoryService,
            ReplaceCategoryOrderService replaceCategoryOrderService,
            CatalogRevisionQueryService catalogRevisionQueryService,
            CatalogWebContextFactory webContextFactory) {
        this.createCategoryService = createCategoryService;
        this.updateCategoryService = updateCategoryService;
        this.replaceCategoryOrderService = replaceCategoryOrderService;
        this.catalogRevisionQueryService = catalogRevisionQueryService;
        this.webContextFactory = webContextFactory;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<CategoryMutationResponse>> create(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateCategoryRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        AuditContext auditContext = webContextFactory.create(authentication, request);
        Category created = createCategoryService.create(body.name(), idempotencyKey, auditContext);
        long revision = catalogRevisionQueryService.currentRevision();
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.created(URI.create("/api/v1/catalog/categories/" + created.categoryId()))
                .eTag("\"category-" + created.version() + "\"")
                .body(CatalogApiEnvelope.of(CategoryMutationResponse.of(created, revision), requestId));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<CategoryMutationResponse>> update(
            @PathVariable UUID categoryId,
            @RequestHeader(name = IF_MATCH) String ifMatch,
            @Valid @RequestBody UpdateCategoryRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        long expectedVersion = CatalogIfMatch.parseVersion("category", ifMatch);
        AuditContext auditContext = webContextFactory.create(authentication, request);
        Category updated = updateCategoryService.rename(categoryId, body.name(), expectedVersion, auditContext);
        long revision = catalogRevisionQueryService.currentRevision();
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.ok()
                .eTag("\"category-" + updated.version() + "\"")
                .body(CatalogApiEnvelope.of(CategoryMutationResponse.of(updated, revision), requestId));
    }

    @PutMapping("/order")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<CatalogRevisionResponse>> replaceOrder(
            @RequestHeader(name = IF_MATCH) String ifMatch,
            @Valid @RequestBody ReplaceCategoryOrderRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        long expectedRevision = CatalogIfMatch.parseVersion("catalog", ifMatch);
        AuditContext auditContext = webContextFactory.create(authentication, request);
        CatalogRevision advanced =
                replaceCategoryOrderService.replaceOrder(body.categoryIds(), expectedRevision, auditContext);
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.ok()
                .eTag("\"catalog-" + advanced.revision() + "\"")
                .body(CatalogApiEnvelope.of(CatalogRevisionResponse.from(advanced), requestId));
    }

    public record CreateCategoryRequest(@NotBlank @Size(min = 1, max = 60) String name) {}

    public record UpdateCategoryRequest(@NotBlank @Size(min = 1, max = 60) String name) {}

    public record ReplaceCategoryOrderRequest(@NotNull @NotEmpty List<UUID> categoryIds) {}
}
