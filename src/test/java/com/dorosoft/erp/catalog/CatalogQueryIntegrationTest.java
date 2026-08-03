package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.api.PublishedMenu;
import com.dorosoft.erp.catalog.application.api.PublishedMenuReader;
import com.dorosoft.erp.catalog.application.api.PublishedProduct;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.product.ChangeProductSalesPolicyService;
import com.dorosoft.erp.catalog.application.product.ChangeSoldOutService;
import com.dorosoft.erp.catalog.application.product.CreateProductCommand;
import com.dorosoft.erp.catalog.application.product.CreateProductService;
import com.dorosoft.erp.catalog.application.product.ReplaceProductOptionsService;
import com.dorosoft.erp.catalog.application.query.CatalogOverview;
import com.dorosoft.erp.catalog.application.query.CatalogOverviewQueryService;
import com.dorosoft.erp.catalog.application.query.ProductListPage;
import com.dorosoft.erp.catalog.application.query.ProductListQueryService;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductOptionRequest;
import com.dorosoft.erp.catalog.domain.query.InvalidCursorException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Catalog 조회 통합 테스트(MENU-06) - 관리자 전체 조회·목록 페이징과 공개 메뉴 Projection을 실제 MySQL로 검증한다")
class CatalogQueryIntegrationTest {

    @Autowired private CreateProductService createProductService;
    @Autowired private ChangeProductSalesPolicyService changeProductSalesPolicyService;
    @Autowired private ChangeSoldOutService changeSoldOutService;
    @Autowired private ReplaceProductOptionsService replaceProductOptionsService;
    @Autowired private PublishedMenuReader publishedMenuReader;
    @Autowired private CatalogOverviewQueryService catalogOverviewQueryService;
    @Autowired private ProductListQueryService productListQueryService;
    @Autowired private CatalogRevisionRepository catalogRevisionRepository;
    @Autowired private JdbcClient jdbcClient;

    private UUID catalogId;
    private AuditContext auditContext;

    @BeforeEach
    void 테이블을_비운다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
        auditContext = CatalogIntegrationSupport.testAuditContext();
    }

    private Product createProduct(UUID categoryId, String name) {
        return createProductService.create(
                new CreateProductCommand(categoryId, name, null, 4500L, null, null, true, false, null), auditContext);
    }

    // --- 공개 메뉴 Projection ---------------------------------------------------

    @Test
    @DisplayName("판매 비활성 상품은 빠지고, 그 결과 빈 Category도 응답에서 빠진다")
    void excludesEmptyCategoryAndInactiveProducts() {
        UUID coffeeCategory = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID emptyCategory = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "빈카테고리", 1);

        Product americano = createProduct(coffeeCategory, "아메리카노");
        Product latte = createProduct(coffeeCategory, "카페라떼");
        changeProductSalesPolicyService.changePolicy(latte.productId(), false, false, latte.version(), auditContext);

        Product onlyProductInEmptyCategory = createProduct(emptyCategory, "숨겨질 상품");
        changeProductSalesPolicyService.changePolicy(
                onlyProductInEmptyCategory.productId(), false, false, onlyProductInEmptyCategory.version(), auditContext);

        PublishedMenu menu = publishedMenuReader.getPublishedMenu();

        assertThat(menu.categories()).hasSize(1);
        assertThat(menu.categories().get(0).categoryId()).isEqualTo(coffeeCategory);
        assertThat(menu.categories().get(0).products()).extracting(PublishedProduct::productId)
                .containsExactly(americano.productId());
    }

    @Test
    @DisplayName("품절 상품은 목록에 남지만 orderable=false로 표시된다")
    void showsSoldOutProductAsNotOrderable() {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        Product product = createProduct(categoryId, "아메리카노");
        changeSoldOutService.changeSoldOut(product.productId(), true, product.version(), auditContext);

        PublishedMenu menu = publishedMenuReader.getPublishedMenu();

        PublishedProduct published = menu.categories().get(0).products().get(0);
        assertThat(published.soldOut()).isTrue();
        assertThat(published.orderable()).isFalse();
    }

    @Test
    @DisplayName("비활성 옵션은 공개 상품의 옵션 목록에서 빠진다")
    void excludesDisabledOptions() {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        Product product = createProduct(categoryId, "아메리카노");
        Product withOptions =
                replaceProductOptionsService.replaceOptions(
                        product.productId(),
                        List.of(
                                new ProductOptionRequest(null, "샷 추가", 500L, true),
                                new ProductOptionRequest(null, "비활성 옵션", 300L, false)),
                        product.version(),
                        auditContext);
        assertThat(withOptions.options()).hasSize(2);

        PublishedMenu menu = publishedMenuReader.getPublishedMenu();

        PublishedProduct published = menu.categories().get(0).products().get(0);
        assertThat(published.options()).hasSize(1);
        assertThat(published.options().get(0).name()).isEqualTo("샷 추가");
    }

    @Test
    @DisplayName("READY Media가 연결된 상품은 CloudFront URL을 반환하고, 없으면 null이다")
    void resolvesImageUrlOnlyForReadyMedia() {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID readyMediaId = CatalogIntegrationSupport.insertReadyProductMedia(jdbcClient, catalogId, "americano");
        Product withMedia =
                createProductService.create(
                        new CreateProductCommand(categoryId, "아메리카노", null, 4500L, readyMediaId, "설명", true, false, null),
                        auditContext);
        Product withoutMedia = createProduct(categoryId, "카페라떼");

        PublishedMenu menu = publishedMenuReader.getPublishedMenu();

        PublishedProduct publishedWithMedia =
                menu.categories().get(0).products().stream()
                        .filter(p -> p.productId().equals(withMedia.productId()))
                        .findFirst()
                        .orElseThrow();
        PublishedProduct publishedWithoutMedia =
                menu.categories().get(0).products().stream()
                        .filter(p -> p.productId().equals(withoutMedia.productId()))
                        .findFirst()
                        .orElseThrow();

        assertThat(publishedWithMedia.imageUrl()).isNotBlank();
        assertThat(publishedWithoutMedia.imageUrl()).isNull();
    }

    @Test
    @DisplayName("catalogRevision은 현재 Catalog Revision과 같다")
    void includesCurrentCatalogRevision() {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        createProduct(categoryId, "아메리카노");

        PublishedMenu menu = publishedMenuReader.getPublishedMenu();

        assertThat(menu.catalogRevision()).isEqualTo(catalogRevisionRepository.findCurrent().orElseThrow().revision());
    }

    // --- 관리자 전체 조회 ---------------------------------------------------------

    @Test
    @DisplayName("관리자 전체 조회는 비활성 Category·Product·Option도 모두 포함한다")
    void overviewIncludesInactiveItems() {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID emptyCategoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "빈카테고리", 1);
        Product product = createProduct(categoryId, "아메리카노");
        Product afterPolicyChange =
                changeProductSalesPolicyService.changePolicy(product.productId(), false, false, product.version(), auditContext);
        replaceProductOptionsService.replaceOptions(
                product.productId(),
                List.of(new ProductOptionRequest(null, "비활성 옵션", 300L, false)),
                afterPolicyChange.version(),
                auditContext);

        CatalogOverview overview = catalogOverviewQueryService.getOverview();

        assertThat(overview.categories()).extracting(c -> c.category().categoryId())
                .containsExactlyInAnyOrder(categoryId, emptyCategoryId);
        var coffeeOverview =
                overview.categories().stream().filter(c -> c.category().categoryId().equals(categoryId)).findFirst().orElseThrow();
        assertThat(coffeeOverview.products()).hasSize(1);
        assertThat(coffeeOverview.products().get(0).salesEnabled()).isFalse();
        assertThat(coffeeOverview.products().get(0).options()).hasSize(1);
        assertThat(coffeeOverview.products().get(0).options().get(0).enabled()).isFalse();
    }

    // --- 관리자 Product 목록 필터·페이징 ------------------------------------------

    @Test
    @DisplayName("categoryId·salesEnabled·soldOut으로 걸러낸다")
    void filtersProductList() {
        UUID coffeeCategory = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID teaCategory = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "차", 1);
        Product americano = createProduct(coffeeCategory, "아메리카노");
        Product latte = createProduct(coffeeCategory, "카페라떼");
        changeProductSalesPolicyService.changePolicy(latte.productId(), false, false, latte.version(), auditContext);
        createProduct(teaCategory, "녹차");

        ProductListPage byCategoryOnly = productListQueryService.list(coffeeCategory, null, null, null, 10);
        assertThat(byCategoryOnly.items()).extracting(Product::productId)
                .containsExactlyInAnyOrder(americano.productId(), latte.productId());

        ProductListPage byCategoryAndSalesEnabled = productListQueryService.list(coffeeCategory, true, null, null, 10);
        assertThat(byCategoryAndSalesEnabled.items()).extracting(Product::productId).containsExactly(americano.productId());
    }

    @Test
    @DisplayName("cursor로 모든 Product를 중복·누락 없이 끝까지 순회한다")
    void paginatesThroughAllProducts() {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        Set<UUID> createdIds = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            createdIds.add(createProduct(categoryId, "상품" + i).productId());
        }

        Set<UUID> collected = new HashSet<>();
        String cursor = null;
        int pageCount = 0;
        do {
            ProductListPage page = productListQueryService.list(null, null, null, cursor, 2);
            page.items().forEach(p -> collected.add(p.productId()));
            cursor = page.nextCursor();
            pageCount++;
            assertThat(pageCount).isLessThanOrEqualTo(10);
        } while (cursor != null);

        assertThat(collected).isEqualTo(createdIds);
        assertThat(pageCount).isEqualTo(3);
    }

    @Test
    @DisplayName("서버가 발급하지 않은 cursor는 InvalidCursorException")
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> productListQueryService.list(null, null, null, "not-a-real-cursor!!", 10))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    @DisplayName("limit이 0 이하면 IllegalArgumentException")
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> productListQueryService.list(null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
