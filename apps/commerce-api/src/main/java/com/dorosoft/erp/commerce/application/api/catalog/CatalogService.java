package com.dorosoft.erp.commerce.application.api.catalog;

import com.dorosoft.erp.commerce.application.api.audit.AuditRecord;
import com.dorosoft.erp.commerce.application.api.audit.CatalogAuditAction;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.ChangeSoldOutCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.SalesMenuCategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.SalesMenuItemView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.SalesMenuView;
import com.dorosoft.erp.commerce.application.api.security.ActorContext;
import com.dorosoft.erp.commerce.application.api.security.ActorContextHolder;
import com.dorosoft.erp.commerce.application.port.audit.AuditRecorderPort;
import com.dorosoft.erp.commerce.application.port.catalog.MenuCategoryRepositoryPort;
import com.dorosoft.erp.commerce.application.port.catalog.ProductRepositoryPort;
import com.dorosoft.erp.commerce.domain.catalog.CatalogRuleException;
import com.dorosoft.erp.commerce.domain.catalog.CatalogStatus;
import com.dorosoft.erp.commerce.domain.catalog.MenuCategory;
import com.dorosoft.erp.commerce.domain.catalog.Product;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.platform.web.ProblemFieldError;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog Use Case 구현.
 *
 * <p>Tenant Scope와 Role은 Controller 입력이 아니라 검증된 Actor Context로 Application에서 다시 확인한다.
 * 업무 변경과 Audit Outbox 기록은 하나의 Local Transaction으로 Commit한다.
 */
@Service
public class CatalogService implements CatalogQueryUseCase, CatalogCommandUseCase {

    static final String CURRENCY_KRW = "KRW";
    static final String TARGET_CATEGORY = "MENU_CATEGORY";
    static final String TARGET_PRODUCT = "PRODUCT";

    private final MenuCategoryRepositoryPort categoryRepository;
    private final ProductRepositoryPort productRepository;
    private final AuditRecorderPort auditRecorder;

    public CatalogService(
            MenuCategoryRepositoryPort categoryRepository,
            ProductRepositoryPort productRepository,
            AuditRecorderPort auditRecorder) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.auditRecorder = auditRecorder;
    }

    // ------------------------------------------------------------------ 조회

    @Override
    @Transactional(readOnly = true)
    public SalesMenuView loadSalesMenu() {
        ActorContext actor = requireActor();
        // 모든 인증 Actor(OWNER·MANAGER·STAFF·KIOSK_DEVICE)가 판매 메뉴를 조회할 수 있다.
        if (!actor.role().canReadSalesMenu()) {
            throw forbidden();
        }

        List<MenuCategory> activeCategories = categoryRepository.findActiveByTenant(actor.tenantId());
        List<Product> sellableProducts = productRepository.findSalesMenuProducts(actor.tenantId());

        Map<UUID, List<SalesMenuItemView>> itemsByCategory = new LinkedHashMap<>();
        for (Product product : sellableProducts) {
            itemsByCategory
                    .computeIfAbsent(product.categoryId(), key -> new ArrayList<>())
                    .add(new SalesMenuItemView(
                            product.id(),
                            product.name(),
                            product.description(),
                            product.price(),
                            product.soldOut(),
                            !product.soldOut(),
                            product.displayOrder(),
                            product.version()));
        }

        List<SalesMenuCategoryView> categories = activeCategories.stream()
                .sorted(Comparator.comparingInt(MenuCategory::displayOrder).thenComparing(MenuCategory::id))
                .map(category -> new SalesMenuCategoryView(
                        category.id(),
                        category.name(),
                        category.displayOrder(),
                        List.copyOf(itemsByCategory.getOrDefault(category.id(), List.of()))))
                .toList();

        return new SalesMenuView(CURRENCY_KRW, categories);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryView> loadManagedCategories() {
        ActorContext actor = requireCatalogManager();
        return categoryRepository.findAllByTenant(actor.tenantId()).stream()
                .sorted(Comparator.comparingInt(MenuCategory::displayOrder).thenComparing(MenuCategory::id))
                .map(CatalogService::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> loadManagedProducts() {
        ActorContext actor = requireCatalogManager();
        return productRepository.findAllByTenant(actor.tenantId()).stream()
                .sorted(Comparator.comparingInt(Product::displayOrder).thenComparing(Product::id))
                .map(CatalogService::toView)
                .toList();
    }

    // ------------------------------------------------------------- Category

    @Override
    @Transactional
    public CategoryView createCategory(CreateCategoryCommand command) {
        ActorContext actor = requireCatalogManager();

        String name = requireText(command.name(), "name");
        int displayOrder = requireInt(command.displayOrder(), "displayOrder");
        CatalogStatus status = toStatus(command.active(), CatalogStatus.ACTIVE);

        MenuCategory category = translateDomain(
                () -> MenuCategory.create(UUID.randomUUID(), actor.tenantId(), name, displayOrder, status));

        if (categoryRepository.existsByTenantAndNameExcludingId(actor.tenantId(), category.name(), null)) {
            throw new ProblemAwareException(
                    CatalogErrorCode.CATEGORY_NAME_DUPLICATED, "이미 사용 중인 Category 이름입니다.");
        }

        MenuCategory saved = categoryRepository.insert(category);
        recordCategoryChanged(actor, saved, "CREATED");
        return toView(saved);
    }

    @Override
    @Transactional
    public CategoryView updateCategory(UUID categoryId, Long expectedVersion, UpdateCategoryCommand command) {
        ActorContext actor = requireCatalogManager();
        long version = requireExpectedVersion(expectedVersion);

        MenuCategory current = categoryRepository.findByTenantAndId(actor.tenantId(), categoryId)
                .orElseThrow(() -> new ProblemAwareException(
                        CatalogErrorCode.CATEGORY_NOT_FOUND, "Category를 찾을 수 없습니다."));

        MenuCategory next = translateDomain(() -> {
            MenuCategory candidate = current;
            if (command.name() != null) {
                candidate = candidate.rename(command.name());
            }
            if (command.displayOrder() != null) {
                candidate = candidate.reorder(command.displayOrder());
            }
            if (command.active() != null) {
                candidate = candidate.changeStatus(toStatus(command.active(), candidate.status()));
            }
            return candidate;
        });

        if (!next.name().equals(current.name())
                && categoryRepository.existsByTenantAndNameExcludingId(actor.tenantId(), next.name(), categoryId)) {
            throw new ProblemAwareException(
                    CatalogErrorCode.CATEGORY_NAME_DUPLICATED, "이미 사용 중인 Category 이름입니다.");
        }

        applyConditionalUpdate(categoryRepository.updateWithVersion(next, version));

        MenuCategory saved = reloadCategory(actor.tenantId(), categoryId);
        recordCategoryChanged(actor, saved, "UPDATED");
        return toView(saved);
    }

    // -------------------------------------------------------------- Product

    @Override
    @Transactional
    public ProductView createProduct(CreateProductCommand command) {
        ActorContext actor = requireCatalogManager();

        UUID categoryId = requireUuid(command.categoryId(), "categoryId");
        String name = requireText(command.name(), "name");
        long price = requireLong(command.price(), "price");
        int displayOrder = requireInt(command.displayOrder(), "displayOrder");
        CatalogStatus status = toStatus(command.active(), CatalogStatus.ACTIVE);

        // 다른 Tenant Category는 존재 여부도 노출하지 않는다.
        requireCategoryOfTenant(actor.tenantId(), categoryId);

        Product product = translateDomain(() -> Product.create(
                UUID.randomUUID(),
                actor.tenantId(),
                categoryId,
                name,
                command.description(),
                price,
                status,
                displayOrder));

        if (productRepository.existsByTenantAndNameExcludingId(actor.tenantId(), product.name(), null)) {
            throw new ProblemAwareException(
                    CatalogErrorCode.PRODUCT_NAME_DUPLICATED, "이미 사용 중인 상품 이름입니다.");
        }

        Product saved = productRepository.insert(product);
        recordProductChanged(actor, saved, "CREATED");
        return toView(saved);
    }

    @Override
    @Transactional
    public ProductView updateProduct(UUID productId, Long expectedVersion, UpdateProductCommand command) {
        ActorContext actor = requireCatalogManager();
        long version = requireExpectedVersion(expectedVersion);

        Product current = productRepository.findByTenantAndId(actor.tenantId(), productId)
                .orElseThrow(() -> new ProblemAwareException(
                        CatalogErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다."));

        if (command.categoryId() != null && !command.categoryId().equals(current.categoryId())) {
            requireCategoryOfTenant(actor.tenantId(), command.categoryId());
        }

        Product next = translateDomain(() -> {
            Product candidate = current;
            if (command.categoryId() != null) {
                candidate = candidate.moveToCategory(command.categoryId());
            }
            if (command.name() != null) {
                candidate = candidate.rename(command.name());
            }
            if (command.description() != null) {
                candidate = candidate.describe(command.description());
            }
            if (command.price() != null) {
                candidate = candidate.changePrice(command.price());
            }
            if (command.displayOrder() != null) {
                candidate = candidate.reorder(command.displayOrder());
            }
            if (command.active() != null) {
                candidate = candidate.changeStatus(toStatus(command.active(), candidate.status()));
            }
            return candidate;
        });

        if (!next.name().equals(current.name())
                && productRepository.existsByTenantAndNameExcludingId(actor.tenantId(), next.name(), productId)) {
            throw new ProblemAwareException(
                    CatalogErrorCode.PRODUCT_NAME_DUPLICATED, "이미 사용 중인 상품 이름입니다.");
        }

        applyConditionalUpdate(productRepository.updateWithVersion(next, version));

        Product saved = reloadProduct(actor.tenantId(), productId);
        recordProductChanged(actor, saved, "UPDATED");
        if (current.price() != saved.price()) {
            // 가격 변경은 별도 Audit Action이다. 기존 주문의 가격 Snapshot은 바꾸지 않는다.
            auditRecorder.record(new AuditRecord(
                    actor,
                    CatalogAuditAction.PRODUCT_PRICE_CHANGED,
                    TARGET_PRODUCT,
                    saved.id(),
                    Map.of(
                            "previousPrice", current.price(),
                            "price", saved.price(),
                            "currency", CURRENCY_KRW,
                            "version", saved.version())));
        }
        return toView(saved);
    }

    @Override
    @Transactional
    public ProductView changeSoldOut(UUID productId, Long expectedVersion, ChangeSoldOutCommand command) {
        ActorContext actor = requireActor();
        // 품절 변경은 OWNER·MANAGER·STAFF만 가능하고 KIOSK_DEVICE는 차단한다.
        if (!actor.canChangeSoldOut()) {
            throw forbidden();
        }
        long version = requireExpectedVersion(expectedVersion);
        boolean soldOut = requireBoolean(command.soldOut(), "soldOut");

        Product current = productRepository.findByTenantAndId(actor.tenantId(), productId)
                .orElseThrow(() -> new ProblemAwareException(
                        CatalogErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다."));

        int affected = productRepository.updateSoldOutWithVersion(actor.tenantId(), productId, soldOut, version);
        applyConditionalUpdate(affected);

        Product saved = reloadProduct(actor.tenantId(), productId);
        // 성공한 변경만 Audit에 남긴다. 충돌한 요청은 위에서 Rollback된다.
        auditRecorder.record(new AuditRecord(
                actor,
                CatalogAuditAction.PRODUCT_SOLD_OUT_CHANGED,
                TARGET_PRODUCT,
                saved.id(),
                Map.of(
                        "previousSoldOut", current.soldOut(),
                        "soldOut", saved.soldOut(),
                        "version", saved.version())));
        return toView(saved);
    }

    // ------------------------------------------------------------- 내부 도구

    private void recordCategoryChanged(ActorContext actor, MenuCategory category, String changeType) {
        auditRecorder.record(new AuditRecord(
                actor,
                CatalogAuditAction.CATEGORY_CHANGED,
                TARGET_CATEGORY,
                category.id(),
                Map.of(
                        "changeType", changeType,
                        "displayOrder", category.displayOrder(),
                        "status", category.status().name(),
                        "version", category.version())));
    }

    private void recordProductChanged(ActorContext actor, Product product, String changeType) {
        auditRecorder.record(new AuditRecord(
                actor,
                CatalogAuditAction.PRODUCT_CHANGED,
                TARGET_PRODUCT,
                product.id(),
                Map.of(
                        "changeType", changeType,
                        "categoryId", product.categoryId().toString(),
                        "displayOrder", product.displayOrder(),
                        "status", product.status().name(),
                        "soldOut", product.soldOut(),
                        "version", product.version())));
    }

    private MenuCategory requireCategoryOfTenant(UUID tenantId, UUID categoryId) {
        return categoryRepository.findByTenantAndId(tenantId, categoryId)
                .orElseThrow(() -> new ProblemAwareException(
                        CatalogErrorCode.CATEGORY_NOT_FOUND,
                        "Category를 찾을 수 없습니다.",
                        List.of(new ProblemFieldError("categoryId", "CATEGORY_NOT_FOUND"))));
    }

    private MenuCategory reloadCategory(UUID tenantId, UUID categoryId) {
        return categoryRepository.findByTenantAndId(tenantId, categoryId)
                .orElseThrow(() -> new ProblemAwareException(
                        CatalogErrorCode.CATEGORY_NOT_FOUND, "Category를 찾을 수 없습니다."));
    }

    private Product reloadProduct(UUID tenantId, UUID productId) {
        return productRepository.findByTenantAndId(tenantId, productId)
                .orElseThrow(() -> new ProblemAwareException(
                        CatalogErrorCode.PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    private void applyConditionalUpdate(int affectedRows) {
        if (affectedRows != 1) {
            throw new ProblemAwareException(
                    CatalogErrorCode.CATALOG_VERSION_CONFLICT,
                    "다른 사용자가 먼저 변경했습니다. 최신 정보를 다시 불러온 뒤 저장하세요.");
        }
    }

    private ActorContext requireCatalogManager() {
        ActorContext actor = requireActor();
        if (!actor.canManageCatalog()) {
            throw forbidden();
        }
        return actor;
    }

    private ActorContext requireActor() {
        Optional<ActorContext> actor = ActorContextHolder.current();
        return actor.orElseThrow(() -> new ProblemAwareException(
                CatalogErrorCode.AUTHENTICATION_REQUIRED, "인증이 필요합니다."));
    }

    private ProblemAwareException forbidden() {
        return new ProblemAwareException(CatalogErrorCode.FORBIDDEN, "현재 권한으로 수행할 수 없습니다.");
    }

    private long requireExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ProblemAwareException(
                    CatalogErrorCode.PRECONDITION_REQUIRED,
                    "현재 version을 If-Match Header로 함께 보내세요.",
                    List.of(new ProblemFieldError("If-Match", "PRECONDITION_REQUIRED")));
        }
        if (expectedVersion < 0L) {
            throw validationFailed("If-Match", "INVALID_VERSION", "version 형식이 올바르지 않습니다.");
        }
        return expectedVersion;
    }

    private static CatalogStatus toStatus(Boolean active, CatalogStatus fallback) {
        if (active == null) {
            return fallback;
        }
        return active ? CatalogStatus.ACTIVE : CatalogStatus.INACTIVE;
    }

    private static <T> T translateDomain(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (CatalogRuleException exception) {
            throw validationFailed(exception.field(), exception.violation().name(), detailFor(exception));
        }
    }

    private static String detailFor(CatalogRuleException exception) {
        return switch (exception.violation()) {
            case PRICE_NEGATIVE -> "가격은 0원 이상의 정수여야 합니다.";
            case PRICE_TOO_LARGE -> "가격이 허용 범위를 넘었습니다.";
            case NAME_REQUIRED -> "이름을 입력하세요.";
            case NAME_TOO_LONG -> "이름이 너무 깁니다.";
            case DESCRIPTION_TOO_LONG -> "설명이 너무 깁니다.";
            case DISPLAY_ORDER_NEGATIVE, DISPLAY_ORDER_TOO_LARGE -> "표시 순서를 확인하세요.";
            default -> "요청 값이 유효하지 않습니다.";
        };
    }

    private static ProblemAwareException validationFailed(String field, String code, String detail) {
        return new ProblemAwareException(
                CatalogErrorCode.VALIDATION_FAILED, detail, List.of(new ProblemFieldError(field, code)));
    }

    private static String requireText(String value, String field) {
        if (value == null) {
            throw validationFailed(field, "REQUIRED", "필수 값을 입력하세요.");
        }
        return value;
    }

    private static UUID requireUuid(UUID value, String field) {
        if (value == null) {
            throw validationFailed(field, "REQUIRED", "필수 값을 입력하세요.");
        }
        return value;
    }

    private static long requireLong(Long value, String field) {
        if (value == null) {
            throw validationFailed(field, "REQUIRED", "필수 값을 입력하세요.");
        }
        return value;
    }

    private static int requireInt(Integer value, String field) {
        if (value == null) {
            throw validationFailed(field, "REQUIRED", "필수 값을 입력하세요.");
        }
        return value;
    }

    private static boolean requireBoolean(Boolean value, String field) {
        if (value == null) {
            throw validationFailed(field, "REQUIRED", "필수 값을 입력하세요.");
        }
        return value;
    }

    private static CategoryView toView(MenuCategory category) {
        return new CategoryView(
                category.id(),
                category.name(),
                category.displayOrder(),
                category.isActive(),
                category.version());
    }

    private static ProductView toView(Product product) {
        return new ProductView(
                product.id(),
                product.categoryId(),
                product.name(),
                product.description(),
                product.price(),
                product.soldOut(),
                product.isActive(),
                product.displayOrder(),
                product.version());
    }
}
