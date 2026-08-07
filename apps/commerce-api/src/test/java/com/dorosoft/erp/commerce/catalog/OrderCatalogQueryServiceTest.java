package com.dorosoft.erp.commerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.ChangeSoldOutCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogErrorCode;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogService;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderLineRequest;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderQuote;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogQueryService;
import com.dorosoft.erp.commerce.application.api.security.ActorContext;
import com.dorosoft.erp.commerce.application.api.security.ActorContextHolder;
import com.dorosoft.erp.commerce.application.api.security.ActorRole;
import com.dorosoft.erp.commerce.domain.catalog.CatalogStatus;
import com.dorosoft.erp.commerce.domain.catalog.MenuCategory;
import com.dorosoft.erp.commerce.domain.catalog.Product;
import com.dorosoft.erp.commerce.support.InMemoryCatalogRepositories;
import com.dorosoft.erp.commerce.support.RecordingAuditRecorder;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SOFT-437·SOFT-438: 주문용 Catalog 조회의 서버 가격 계산, 판매 가능 재검증과 Tenant 격리 검증.
 */
class OrderCatalogQueryServiceTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID STORE_A = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");

    private InMemoryCatalogRepositories repositories;
    private CatalogService catalogService;
    private OrderCatalogQueryService orderCatalogQueryService;

    @BeforeEach
    void setUp() {
        repositories = new InMemoryCatalogRepositories();
        catalogService = new CatalogService(
                repositories.categoryRepository(), repositories.productRepository(), new RecordingAuditRecorder());
        orderCatalogQueryService = new OrderCatalogQueryService(
                repositories.categoryRepository(), repositories.productRepository());
    }

    @AfterEach
    void tearDown() {
        ActorContextHolder.clear();
    }

    // ------------------------------------------------------ 가격 변조 차단

    @Test
    void theOrderLineContractHasNoPriceFieldSoAClientCannotSendOne() {
        RecordComponent[] components = OrderLineRequest.class.getRecordComponents();

        assertThat(components).extracting(RecordComponent::getName)
                .containsExactly("productId", "quantity");
    }

    @Test
    void theServerPriceIsUsedNoMatterWhatTheClientBelievesThePriceIs() {
        UUID productId = seedSellableProduct("아메리카노", 4500L);
        authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);

        // Client는 가격을 보낼 수단이 없다. 상품과 수량만 전달한다.
        OrderQuote quote = orderCatalogQueryService.quoteOrderLines(List.of(new OrderLineRequest(productId, 2)));

        assertThat(quote.lines()).hasSize(1);
        assertThat(quote.lines().get(0).product().unitPrice()).isEqualTo(4500L);
        assertThat(quote.lines().get(0).lineAmount()).isEqualTo(9000L);
        assertThat(quote.totalAmount()).isEqualTo(9000L);
        assertThat(quote.currency()).isEqualTo("KRW");
    }

    @Test
    void theSnapshotCarriesTheCatalogProductNameAndUnitPrice() {
        UUID productId = seedSellableProduct("아메리카노", 4500L);
        authenticate(TENANT_A, ActorRole.STAFF);

        OrderQuote quote = orderCatalogQueryService.quoteOrderLines(List.of(new OrderLineRequest(productId, 1)));

        assertThat(quote.lines().get(0).product().productId()).isEqualTo(productId);
        assertThat(quote.lines().get(0).product().productName()).isEqualTo("아메리카노");
        assertThat(quote.lines().get(0).product().unitPrice()).isEqualTo(4500L);
    }

    @Test
    void aQuoteTakenBeforeAPriceChangeKeepsItsSnapshotWhileANewQuoteUsesTheNewPrice() {
        UUID productId = seedSellableProduct("아메리카노", 4500L);
        authenticate(TENANT_A, ActorRole.STAFF);
        OrderQuote earlierQuote = orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(productId, 2)));

        authenticate(TENANT_A, ActorRole.OWNER);
        catalogService.updateProduct(productId, 0L, new UpdateProductCommand(null, null, null, 5000L, null, null));

        // 이미 만들어진 Snapshot은 Catalog 변경의 영향을 받지 않는다 (FR-CATALOG-005).
        assertThat(earlierQuote.lines().get(0).product().unitPrice()).isEqualTo(4500L);
        assertThat(earlierQuote.lines().get(0).lineAmount()).isEqualTo(9000L);
        assertThat(earlierQuote.totalAmount()).isEqualTo(9000L);

        OrderQuote laterQuote = orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(productId, 2)));
        assertThat(laterQuote.lines().get(0).product().unitPrice()).isEqualTo(5000L);
        assertThat(laterQuote.totalAmount()).isEqualTo(10000L);
    }

    @Test
    void aRenamedProductDoesNotChangeAnEarlierSnapshot() {
        UUID productId = seedSellableProduct("아메리카노", 4500L);
        authenticate(TENANT_A, ActorRole.STAFF);
        OrderQuote earlierQuote = orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(productId, 1)));

        authenticate(TENANT_A, ActorRole.OWNER);
        catalogService.updateProduct(productId, 0L, new UpdateProductCommand(null, "따뜻한 아메리카노", null, null, null, null));

        assertThat(earlierQuote.lines().get(0).product().productName()).isEqualTo("아메리카노");
    }

    // -------------------------------------------------------- 판매 가능 재검증

    @Test
    void aProductSoldOutAfterTheMenuWasLoadedIsRejected() {
        UUID productId = seedSellableProduct("아메리카노", 4500L);
        authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);
        // Kiosk가 메뉴를 받은 시점에는 판매 가능했다.
        assertThat(catalogService.loadSalesMenu().categories().get(0).products()).hasSize(1);

        authenticate(TENANT_A, ActorRole.STAFF);
        catalogService.changeSoldOut(productId, 0L, new ChangeSoldOutCommand(true));

        authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);
        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(productId, 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_SELLABLE));
    }

    @Test
    void aDeactivatedProductIsRejected() {
        UUID productId = seedSellableProduct("아메리카노", 4500L);
        authenticate(TENANT_A, ActorRole.OWNER);
        catalogService.updateProduct(productId, 0L, new UpdateProductCommand(null, null, null, null, null, false));

        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(productId, 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_SELLABLE));
    }

    @Test
    void aProductUnderADeactivatedCategoryIsRejected() {
        MenuCategory category = repositories.seedCategory(TENANT_A, "커피", 1, CatalogStatus.ACTIVE);
        Product product =
                repositories.seedProduct(TENANT_A, category.id(), "아메리카노", 4500L, CatalogStatus.ACTIVE, false);
        authenticate(TENANT_A, ActorRole.OWNER);
        catalogService.updateCategory(category.id(), 0L, new UpdateCategoryCommand(null, null, false));

        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(product.id(), 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_SELLABLE));
    }

    @Test
    void oneUnsellableLineRejectsTheWholeOrder() {
        UUID sellable = seedSellableProduct("아메리카노", 4500L);
        UUID soldOut = seedSellableProduct("카페라떼", 5000L);
        authenticate(TENANT_A, ActorRole.STAFF);
        catalogService.changeSoldOut(soldOut, 0L, new ChangeSoldOutCommand(true));

        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(List.of(
                new OrderLineRequest(sellable, 1), new OrderLineRequest(soldOut, 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_SELLABLE));
    }

    // --------------------------------------------------------------- Tenant

    @Test
    void anotherTenantProductIsReportedAsNotFoundWithoutRevealingItExists() {
        MenuCategory foreignCategory = repositories.seedCategory(TENANT_B, "디저트", 1, CatalogStatus.ACTIVE);
        Product foreignProduct = repositories.seedProduct(
                TENANT_B, foreignCategory.id(), "치즈케이크", 6500L, CatalogStatus.ACTIVE, false);
        authenticate(TENANT_A, ActorRole.STAFF);

        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(foreignProduct.id(), 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void anUnknownProductIsRejected() {
        authenticate(TENANT_A, ActorRole.STAFF);

        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(UUID.randomUUID(), 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }

    // ----------------------------------------------------------------- 입력

    @Test
    void anEmptyOrderIsRejected() {
        authenticate(TENANT_A, ActorRole.STAFF);

        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(List.of()))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.VALIDATION_FAILED));
    }

    @Test
    void aQuantityBelowOneIsRejected() {
        UUID productId = seedSellableProduct("아메리카노", 4500L);
        authenticate(TENANT_A, ActorRole.STAFF);

        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(productId, 0))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.VALIDATION_FAILED));
    }

    @Test
    void aZeroPricedProductStillQuotesToZero() {
        UUID productId = seedSellableProduct("시음 커피", 0L);
        authenticate(TENANT_A, ActorRole.STAFF);

        OrderQuote quote = orderCatalogQueryService.quoteOrderLines(List.of(new OrderLineRequest(productId, 3)));

        assertThat(quote.totalAmount()).isZero();
    }

    @Test
    void anUnauthenticatedQuoteIsRejected() {
        ActorContextHolder.clear();

        assertThatThrownBy(() -> orderCatalogQueryService.quoteOrderLines(
                List.of(new OrderLineRequest(UUID.randomUUID(), 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(problemCode(error))
                        .isEqualTo(CatalogErrorCode.AUTHENTICATION_REQUIRED));
    }

    @Test
    void multipleLinesAreSummedByTheServer() {
        UUID americano = seedSellableProduct("아메리카노", 4500L);
        UUID latte = seedSellableProduct("카페라떼", 5000L);
        authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);

        OrderQuote quote = orderCatalogQueryService.quoteOrderLines(List.of(
                new OrderLineRequest(americano, 2), new OrderLineRequest(latte, 1)));

        assertThat(quote.totalAmount()).isEqualTo(4500L * 2 + 5000L);
    }

    private UUID seedSellableProduct(String name, long price) {
        MenuCategory category = repositories.findOrSeedCategory(TENANT_A, "커피");
        return repositories.seedProduct(TENANT_A, category.id(), name, price, CatalogStatus.ACTIVE, false).id();
    }

    private static void authenticate(UUID tenantId, ActorRole role) {
        ActorContextHolder.set(new ActorContext(
                tenantId, STORE_A, role.requiredActorType(), UUID.randomUUID(), role));
    }

    private static CatalogErrorCode problemCode(Throwable error) {
        return CatalogErrorCode.valueOf(((ProblemAwareException) error).code().code());
    }
}
