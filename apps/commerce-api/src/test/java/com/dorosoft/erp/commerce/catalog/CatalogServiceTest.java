package com.dorosoft.erp.commerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.commerce.application.api.audit.CatalogAuditAction;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.ChangeSoldOutCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogErrorCode;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogService;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.SalesMenuView;
import com.dorosoft.erp.commerce.application.api.security.ActorContext;
import com.dorosoft.erp.commerce.application.api.security.ActorContextHolder;
import com.dorosoft.erp.commerce.application.api.security.ActorRole;
import com.dorosoft.erp.commerce.application.api.security.ActorType;
import com.dorosoft.erp.commerce.domain.catalog.CatalogStatus;
import com.dorosoft.erp.commerce.domain.catalog.MenuCategory;
import com.dorosoft.erp.commerce.domain.catalog.Product;
import com.dorosoft.erp.commerce.support.InMemoryCatalogRepositories;
import com.dorosoft.erp.commerce.support.RecordingAuditRecorder;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SOFT-435: Tenant Scope, 가격·입력 검증, Role 인가, 비활성화와 낙관적 충돌 규칙 검증.
 */
class CatalogServiceTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID STORE_A = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");

    private InMemoryCatalogRepositories repositories;
    private RecordingAuditRecorder auditRecorder;
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        repositories = new InMemoryCatalogRepositories();
        auditRecorder = new RecordingAuditRecorder();
        catalogService = new CatalogService(
                repositories.categoryRepository(), repositories.productRepository(), auditRecorder);
    }

    @AfterEach
    void tearDown() {
        ActorContextHolder.clear();
    }

    // ------------------------------------------------------------- Tenant

    @Nested
    @DisplayName("Tenant Scope")
    class TenantScope {

        @Test
        void ownerReadsOnlyItsOwnTenantMenu() {
            MenuCategory categoryA = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            repositories.seedProduct(TENANT_A, categoryA.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            MenuCategory categoryB = repositories.seedCategory(TENANT_B, "디저트", 1, CatalogStatus.ACTIVE);
            repositories.seedProduct(TENANT_B, categoryB.id(), "치즈케이크", 6500L, CatalogStatus.ACTIVE, false);

            authenticate(TENANT_A, ActorRole.OWNER);
            SalesMenuView menu = catalogService.loadSalesMenu();

            assertThat(menu.categories()).hasSize(1);
            assertThat(menu.categories().get(0).name()).isEqualTo("커피");
            assertThat(menu.categories().get(0).products())
                    .extracting("name")
                    .containsExactly("아메리카노");
        }

        @Test
        void otherTenantCategoryCannotBeUpdated() {
            MenuCategory foreignCategory = repositories.seedCategory(TENANT_B, "디저트", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.updateCategory(
                    foreignCategory.id(), 0L, new UpdateCategoryCommand("변경", null, null)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));

            assertThat(repositories.category(foreignCategory.id()).name()).isEqualTo("디저트");
        }

        @Test
        void otherTenantProductCannotBeUpdated() {
            MenuCategory foreignCategory = repositories.seedCategory(TENANT_B, "디저트", 1, CatalogStatus.ACTIVE);
            Product foreignProduct =
                    repositories.seedProduct(TENANT_B, foreignCategory.id(), "치즈케이크", 6500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.MANAGER);

            assertThatThrownBy(() -> catalogService.updateProduct(
                    foreignProduct.id(), 0L, new UpdateProductCommand(null, null, null, 100L, null, null)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));

            assertThat(repositories.product(foreignProduct.id()).price()).isEqualTo(6500L);
        }

        @Test
        void otherTenantProductSoldOutCannotBeChanged() {
            MenuCategory foreignCategory = repositories.seedCategory(TENANT_B, "디저트", 1, CatalogStatus.ACTIVE);
            Product foreignProduct =
                    repositories.seedProduct(TENANT_B, foreignCategory.id(), "치즈케이크", 6500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.STAFF);

            assertThatThrownBy(() -> catalogService.changeSoldOut(
                    foreignProduct.id(), 0L, new ChangeSoldOutCommand(true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));

            assertThat(repositories.product(foreignProduct.id()).soldOut()).isFalse();
            assertThat(auditRecorder.records()).isEmpty();
        }

        @Test
        void productCannotBeCreatedUnderAnotherTenantCategory() {
            MenuCategory foreignCategory = repositories.seedCategory(TENANT_B, "디저트", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.createProduct(new CreateProductCommand(
                    foreignCategory.id(), "아메리카노", null, 4500L, 1, true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));
        }
    }

    // -------------------------------------------------------------- 가격

    @Nested
    @DisplayName("가격 규칙")
    class PriceRules {

        @Test
        void zeroPriceIsAccepted() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            ProductView created = catalogService.createProduct(
                    new CreateProductCommand(category.id(), "시음 커피", null, 0L, 1, true));

            assertThat(created.price()).isZero();
        }

        @Test
        void positivePriceIsAccepted() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            ProductView created = catalogService.createProduct(
                    new CreateProductCommand(category.id(), "아메리카노", null, 4500L, 1, true));

            assertThat(created.price()).isEqualTo(4500L);
        }

        @Test
        void negativePriceIsRejectedAndNothingIsStored() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.createProduct(
                    new CreateProductCommand(category.id(), "잘못된 상품", null, -1L, 1, true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.VALIDATION_FAILED));

            authenticate(TENANT_A, ActorRole.OWNER);
            assertThat(catalogService.loadManagedProducts()).isEmpty();
            assertThat(auditRecorder.records()).isEmpty();
        }

        @Test
        void negativePriceUpdateDoesNotChangeStoredPrice() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.updateProduct(
                    product.id(), 0L, new UpdateProductCommand(null, null, null, -500L, null, null)))
                    .isInstanceOf(ProblemAwareException.class);

            assertThat(repositories.product(product.id()).price()).isEqualTo(4500L);
            assertThat(repositories.product(product.id()).version()).isZero();
            assertThat(auditRecorder.records()).isEmpty();
        }

        @Test
        void priceAboveTheAllowedRangeIsRejected() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.createProduct(
                    new CreateProductCommand(category.id(), "비정상 가격", null, 100_000_001L, 1, true)))
                    .isInstanceOf(ProblemAwareException.class);
        }

        @Test
        void priceChangeIsAuditedSeparately() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.OWNER);

            catalogService.updateProduct(
                    product.id(), 0L, new UpdateProductCommand(null, null, null, 5000L, null, null));

            assertThat(auditRecorder.actions())
                    .containsExactly(CatalogAuditAction.PRODUCT_CHANGED, CatalogAuditAction.PRODUCT_PRICE_CHANGED);
        }
    }

    // -------------------------------------------------------------- 입력

    @Nested
    @DisplayName("입력 검증")
    class InputValidation {

        @Test
        void blankCategoryNameIsRejected() {
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.createCategory(new CreateCategoryCommand("   ", 1, true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.VALIDATION_FAILED));
        }

        @Test
        void missingProductNameIsRejected() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.createProduct(
                    new CreateProductCommand(category.id(), null, null, 4500L, 1, true)))
                    .isInstanceOf(ProblemAwareException.class);
        }

        @Test
        void unknownCategoryIsRejected() {
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.createProduct(
                    new CreateProductCommand(UUID.randomUUID(), "아메리카노", null, 4500L, 1, true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.CATEGORY_NOT_FOUND));
        }

        @Test
        void negativeDisplayOrderIsRejected() {
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.createCategory(new CreateCategoryCommand("커피", -1, true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.VALIDATION_FAILED));
        }

        @Test
        void duplicatedCategoryNameIsRejected() {
            repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.createCategory(new CreateCategoryCommand("커피", 2, true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.CATEGORY_NAME_DUPLICATED));
        }

        @Test
        void missingVersionIsRejectedWithPreconditionRequired() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            authenticate(TENANT_A, ActorRole.OWNER);

            assertThatThrownBy(() -> catalogService.updateCategory(
                    category.id(), null, new UpdateCategoryCommand("차", null, null)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.PRECONDITION_REQUIRED));
        }

        @Test
        void missingSoldOutValueIsRejected() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.STAFF);

            assertThatThrownBy(() -> catalogService.changeSoldOut(
                    product.id(), 0L, new ChangeSoldOutCommand(null)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.VALIDATION_FAILED));
        }
    }

    // -------------------------------------------------------------- 권한

    @Nested
    @DisplayName("Role 인가")
    class RoleAuthorization {

        @Test
        void ownerCanManageCatalog() {
            authenticate(TENANT_A, ActorRole.OWNER);
            CategoryView created = catalogService.createCategory(new CreateCategoryCommand("커피", 1, true));
            assertThat(created.active()).isTrue();
        }

        @Test
        void managerCanManageCatalog() {
            authenticate(TENANT_A, ActorRole.MANAGER);
            CategoryView created = catalogService.createCategory(new CreateCategoryCommand("커피", 1, true));
            assertThat(created.name()).isEqualTo("커피");
        }

        @Test
        void staffCannotManageCategories() {
            authenticate(TENANT_A, ActorRole.STAFF);

            assertThatThrownBy(() -> catalogService.createCategory(new CreateCategoryCommand("커피", 1, true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error)).isEqualTo(CatalogErrorCode.FORBIDDEN));
        }

        @Test
        void staffCannotChangeProductPrice() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.STAFF);

            assertThatThrownBy(() -> catalogService.updateProduct(
                    product.id(), 0L, new UpdateProductCommand(null, null, null, 100L, null, null)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error)).isEqualTo(CatalogErrorCode.FORBIDDEN));

            assertThat(repositories.product(product.id()).price()).isEqualTo(4500L);
        }

        @Test
        void staffCanChangeSoldOut() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.STAFF);

            ProductView changed = catalogService.changeSoldOut(product.id(), 0L, new ChangeSoldOutCommand(true));

            assertThat(changed.soldOut()).isTrue();
            assertThat(changed.version()).isEqualTo(1L);
            assertThat(auditRecorder.actions()).containsExactly(CatalogAuditAction.PRODUCT_SOLD_OUT_CHANGED);
        }

        @Test
        void staffCanReadTheOperationsListToChangeSoldOut() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, true);
            authenticate(TENANT_A, ActorRole.STAFF);

            assertThat(catalogService.loadManagedProducts()).hasSize(1);
            assertThat(catalogService.loadManagedCategories()).hasSize(1);
        }

        @Test
        void kioskDeviceCannotReadTheOperationsList() {
            authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);

            assertThatThrownBy(() -> catalogService.loadManagedProducts())
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error)).isEqualTo(CatalogErrorCode.FORBIDDEN));
        }

        @Test
        void kioskDeviceCanReadSalesMenu() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);

            assertThat(catalogService.loadSalesMenu().categories()).hasSize(1);
        }

        @Test
        void kioskDeviceCannotManageCatalog() {
            authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);

            assertThatThrownBy(() -> catalogService.createCategory(new CreateCategoryCommand("커피", 1, true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error)).isEqualTo(CatalogErrorCode.FORBIDDEN));
        }

        @Test
        void kioskDeviceCannotChangeSoldOut() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);

            assertThatThrownBy(() -> catalogService.changeSoldOut(
                    product.id(), 0L, new ChangeSoldOutCommand(true)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error)).isEqualTo(CatalogErrorCode.FORBIDDEN));

            assertThat(repositories.product(product.id()).soldOut()).isFalse();
        }

        @Test
        void unauthenticatedRequestIsRejected() {
            ActorContextHolder.clear();

            assertThatThrownBy(() -> catalogService.loadSalesMenu())
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.AUTHENTICATION_REQUIRED));
        }
    }

    // ------------------------------------------------------------ 비활성화

    @Nested
    @DisplayName("비활성화")
    class Deactivation {

        @Test
        void inactiveCategoryIsHiddenFromSalesMenuButRowRemains() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.OWNER);

            catalogService.updateCategory(category.id(), 0L, new UpdateCategoryCommand(null, null, false));

            assertThat(catalogService.loadSalesMenu().categories()).isEmpty();
            assertThat(repositories.category(category.id())).isNotNull();
            assertThat(catalogService.loadManagedCategories()).hasSize(1);
            assertThat(catalogService.loadManagedProducts()).hasSize(1);
        }

        @Test
        void inactiveProductIsHiddenFromSalesMenuButRowRemains() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.OWNER);

            catalogService.updateProduct(
                    product.id(), 0L, new UpdateProductCommand(null, null, null, null, null, false));

            assertThat(catalogService.loadSalesMenu().categories().get(0).products()).isEmpty();
            assertThat(repositories.product(product.id())).isNotNull();
            assertThat(catalogService.loadManagedProducts()).hasSize(1);
        }

        @Test
        void soldOutProductIsExcludedFromTheSalesMenuButRowRemains() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.STAFF);
            catalogService.changeSoldOut(product.id(), 0L, new ChangeSoldOutCommand(true));

            authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);
            assertThat(catalogService.loadSalesMenu().categories().get(0).products()).isEmpty();

            // 품절은 판매 메뉴에서만 빠지고 Row와 운영 목록에는 그대로 남는다.
            authenticate(TENANT_A, ActorRole.STAFF);
            assertThat(catalogService.loadManagedProducts()).hasSize(1);
            assertThat(repositories.product(product.id()).soldOut()).isTrue();
        }

        @Test
        void resumedProductReappearsInTheSalesMenu() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, true);
            authenticate(TENANT_A, ActorRole.STAFF);

            assertThat(catalogService.loadSalesMenu().categories().get(0).products()).isEmpty();
            catalogService.changeSoldOut(product.id(), 0L, new ChangeSoldOutCommand(false));

            assertThat(catalogService.loadSalesMenu().categories().get(0).products()).hasSize(1);
        }
    }

    // -------------------------------------------------------------- 충돌

    @Nested
    @DisplayName("낙관적 충돌")
    class OptimisticConflict {

        @Test
        void staleVersionSoldOutChangeIsRejected() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.STAFF);

            catalogService.changeSoldOut(product.id(), 0L, new ChangeSoldOutCommand(true));
            auditRecorder.clear();

            assertThatThrownBy(() -> catalogService.changeSoldOut(
                    product.id(), 0L, new ChangeSoldOutCommand(false)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.CATALOG_VERSION_CONFLICT));

            assertThat(repositories.product(product.id()).soldOut()).isTrue();
            assertThat(repositories.product(product.id()).version()).isEqualTo(1L);
            assertThat(auditRecorder.records()).isEmpty();
        }

        @Test
        void staleVersionProductUpdateIsRejected() {
            MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
            Product product =
                    repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
            authenticate(TENANT_A, ActorRole.OWNER);

            catalogService.updateProduct(
                    product.id(), 0L, new UpdateProductCommand(null, null, null, 5000L, null, null));

            assertThatThrownBy(() -> catalogService.updateProduct(
                    product.id(), 0L, new UpdateProductCommand(null, null, null, 9000L, null, null)))
                    .isInstanceOf(ProblemAwareException.class)
                    .satisfies(error -> assertThat(problemCode(error))
                            .isEqualTo(CatalogErrorCode.CATALOG_VERSION_CONFLICT));

            assertThat(repositories.product(product.id()).price()).isEqualTo(5000L);
            assertThat(repositories.product(product.id()).version()).isEqualTo(1L);
        }
    }

    private static void authenticate(UUID tenantId, ActorRole role) {
        ActorContextHolder.set(new ActorContext(
                tenantId, STORE_A, role.requiredActorType(), UUID.randomUUID(), role));
    }

    private static CatalogErrorCode problemCode(Throwable error) {
        ProblemAwareException problem = (ProblemAwareException) error;
        return CatalogErrorCode.valueOf(problem.code().code());
    }

    @Test
    void actorTypeMustMatchRole() {
        assertThatThrownBy(() -> new ActorContext(
                TENANT_A, STORE_A, ActorType.DEVICE, UUID.randomUUID(), ActorRole.OWNER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
