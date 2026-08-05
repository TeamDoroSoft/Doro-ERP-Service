package com.dorosoft.erp.catalog.presentation.product;

import com.dorosoft.erp.catalog.application.category.ReplaceProductOrderInCategoryService;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.product.ChangeProductSalesPolicyService;
import com.dorosoft.erp.catalog.application.product.ChangeSoldOutService;
import com.dorosoft.erp.catalog.application.product.CreateProductCommand;
import com.dorosoft.erp.catalog.application.product.CreateProductService;
import com.dorosoft.erp.catalog.application.product.ReplaceProductBasicInfoCommand;
import com.dorosoft.erp.catalog.application.product.ReplaceProductOptionsService;
import com.dorosoft.erp.catalog.application.product.UpdateProductService;
import com.dorosoft.erp.catalog.application.query.CatalogRevisionQueryService;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductOptionRequest;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import com.dorosoft.erp.catalog.presentation.common.CatalogApiEnvelope;
import com.dorosoft.erp.catalog.presentation.common.CatalogIfMatch;
import com.dorosoft.erp.catalog.presentation.common.CatalogRequestId;
import com.dorosoft.erp.catalog.presentation.common.CatalogWebContextFactory;
import com.dorosoft.erp.catalog.presentation.dto.CatalogRevisionResponse;
import com.dorosoft.erp.catalog.presentation.dto.ProductMutationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class ProductController {
    private static final String IF_MATCH = "If-Match";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final CreateProductService createProductService;
    private final UpdateProductService updateProductService;
    private final ReplaceProductOrderInCategoryService replaceProductOrderInCategoryService;
    private final ReplaceProductOptionsService replaceProductOptionsService;
    private final ChangeProductSalesPolicyService changeProductSalesPolicyService;
    private final ChangeSoldOutService changeSoldOutService;
    private final CatalogRevisionQueryService catalogRevisionQueryService;
    private final CatalogWebContextFactory webContextFactory;

    public ProductController(
            CreateProductService createProductService,
            UpdateProductService updateProductService,
            ReplaceProductOrderInCategoryService replaceProductOrderInCategoryService,
            ReplaceProductOptionsService replaceProductOptionsService,
            ChangeProductSalesPolicyService changeProductSalesPolicyService,
            ChangeSoldOutService changeSoldOutService,
            CatalogRevisionQueryService catalogRevisionQueryService,
            CatalogWebContextFactory webContextFactory) {
        this.createProductService = createProductService;
        this.updateProductService = updateProductService;
        this.replaceProductOrderInCategoryService = replaceProductOrderInCategoryService;
        this.replaceProductOptionsService = replaceProductOptionsService;
        this.changeProductSalesPolicyService = changeProductSalesPolicyService;
        this.changeSoldOutService = changeSoldOutService;
        this.catalogRevisionQueryService = catalogRevisionQueryService;
        this.webContextFactory = webContextFactory;
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<ProductMutationResponse>> create(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateProductRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        AuditContext auditContext = webContextFactory.create(authentication, request);
        Product created =
                createProductService.create(
                        new CreateProductCommand(
                                body.categoryId(),
                                body.name(),
                                body.description(),
                                body.basePrice(),
                                body.mediaId(),
                                body.imageAltText(),
                                body.salesEnabled(),
                                body.stockManaged(),
                                idempotencyKey),
                        auditContext);
        long revision = catalogRevisionQueryService.currentRevision();
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.created(URI.create("/api/v1/catalog/products/" + created.productId()))
                .eTag("\"product-" + created.version() + "\"")
                .body(CatalogApiEnvelope.of(ProductMutationResponse.of(created, revision), requestId));
    }

    @PutMapping("/products/{productId}")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<ProductMutationResponse>> update(
            @PathVariable UUID productId,
            @RequestHeader(name = IF_MATCH) String ifMatch,
            @Valid @RequestBody ReplaceProductBasicInfoRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        long expectedVersion = CatalogIfMatch.parseVersion("product", ifMatch);
        AuditContext auditContext = webContextFactory.create(authentication, request);
        Product updated =
                updateProductService.replaceBasicInfo(
                        productId,
                        new ReplaceProductBasicInfoCommand(
                                body.categoryId(),
                                body.name(),
                                body.description(),
                                body.basePrice(),
                                body.mediaId(),
                                body.imageAltText(),
                                body.salesEnabled(),
                                body.stockManaged()),
                        expectedVersion,
                        auditContext);
        long revision = catalogRevisionQueryService.currentRevision();
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.ok()
                .eTag("\"product-" + updated.version() + "\"")
                .body(CatalogApiEnvelope.of(ProductMutationResponse.of(updated, revision), requestId));
    }

    @PutMapping("/categories/{categoryId}/product-order")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<CatalogRevisionResponse>> replaceProductOrder(
            @PathVariable UUID categoryId,
            @RequestHeader(name = IF_MATCH) String ifMatch,
            @Valid @RequestBody ReplaceProductOrderRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        long expectedRevision = CatalogIfMatch.parseVersion("catalog", ifMatch);
        AuditContext auditContext = webContextFactory.create(authentication, request);
        CatalogRevision advanced =
                replaceProductOrderInCategoryService.replaceOrder(
                        categoryId, body.productIds(), expectedRevision, auditContext);
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.ok()
                .eTag("\"catalog-" + advanced.revision() + "\"")
                .body(CatalogApiEnvelope.of(CatalogRevisionResponse.from(advanced), requestId));
    }

    @PutMapping("/products/{productId}/options")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<ProductMutationResponse>> replaceOptions(
            @PathVariable UUID productId,
            @RequestHeader(name = IF_MATCH) String ifMatch,
            @Valid @RequestBody ReplaceProductOptionsRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        long expectedVersion = CatalogIfMatch.parseVersion("product", ifMatch);
        AuditContext auditContext = webContextFactory.create(authentication, request);
        List<ProductOptionRequest> options =
                body.options().stream()
                        .map(o -> new ProductOptionRequest(o.optionId(), o.name(), o.additionalPrice(), o.enabled()))
                        .toList();
        Product updated = replaceProductOptionsService.replaceOptions(productId, options, expectedVersion, auditContext);
        long revision = catalogRevisionQueryService.currentRevision();
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.ok()
                .eTag("\"product-" + updated.version() + "\"")
                .body(CatalogApiEnvelope.of(ProductMutationResponse.of(updated, revision), requestId));
    }

    @PatchMapping("/products/{productId}/sales-policy")
    @PreAuthorize("hasAuthority('catalog.manage')")
    public ResponseEntity<CatalogApiEnvelope<ProductMutationResponse>> changeSalesPolicy(
            @PathVariable UUID productId,
            @RequestHeader(name = IF_MATCH) String ifMatch,
            @Valid @RequestBody ChangeSalesPolicyRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        long expectedVersion = CatalogIfMatch.parseVersion("product", ifMatch);
        AuditContext auditContext = webContextFactory.create(authentication, request);
        Product updated =
                changeProductSalesPolicyService.changePolicy(
                        productId, body.salesEnabled(), body.stockManaged(), expectedVersion, auditContext);
        long revision = catalogRevisionQueryService.currentRevision();
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.ok()
                .eTag("\"product-" + updated.version() + "\"")
                .body(CatalogApiEnvelope.of(ProductMutationResponse.of(updated, revision), requestId));
    }

    @PatchMapping("/products/{productId}/sold-out")
    @PreAuthorize("hasAuthority('catalog.soldout.update')")
    public ResponseEntity<CatalogApiEnvelope<ProductMutationResponse>> changeSoldOut(
            @PathVariable UUID productId,
            @RequestHeader(name = IF_MATCH) String ifMatch,
            @Valid @RequestBody ChangeSoldOutRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        long expectedVersion = CatalogIfMatch.parseVersion("product", ifMatch);
        AuditContext auditContext = webContextFactory.create(authentication, request);
        Product updated = changeSoldOutService.changeSoldOut(productId, body.soldOut(), expectedVersion, auditContext);
        long revision = catalogRevisionQueryService.currentRevision();
        String requestId = CatalogRequestId.from(request);
        return ResponseEntity.ok()
                .eTag("\"product-" + updated.version() + "\"")
                .body(CatalogApiEnvelope.of(ProductMutationResponse.of(updated, revision), requestId));
    }

    public record CreateProductRequest(
            @NotNull UUID categoryId,
            @jakarta.validation.constraints.NotBlank @Size(min = 1, max = 100) String name,
            @Size(max = 2000) String description,
            @PositiveOrZero long basePrice,
            UUID mediaId,
            String imageAltText,
            @NotNull Boolean salesEnabled,
            @NotNull Boolean stockManaged) {}

    public record ReplaceProductBasicInfoRequest(
            @NotNull UUID categoryId,
            @jakarta.validation.constraints.NotBlank @Size(min = 1, max = 100) String name,
            @Size(max = 2000) String description,
            @PositiveOrZero long basePrice,
            UUID mediaId,
            String imageAltText,
            @NotNull Boolean salesEnabled,
            @NotNull Boolean stockManaged) {}

    public record ReplaceProductOrderRequest(@NotNull @NotEmpty List<UUID> productIds) {}

    public record ReplaceProductOptionsRequest(@NotNull @NotEmpty List<ProductOptionEntry> options) {

        public record ProductOptionEntry(
                UUID optionId,
                @jakarta.validation.constraints.NotBlank @Size(min = 1, max = 100) String name,
                @PositiveOrZero long additionalPrice,
                @NotNull Boolean enabled) {}
    }

    public record ChangeSalesPolicyRequest(@NotNull Boolean salesEnabled, @NotNull Boolean stockManaged) {}

    public record ChangeSoldOutRequest(@NotNull Boolean soldOut) {}
}
