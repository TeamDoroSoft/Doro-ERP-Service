package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.testsupport.MySqlTestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.category.CreateCategoryService;
import com.dorosoft.erp.catalog.application.category.UpdateCategoryService;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.product.ChangeProductSalesPolicyService;
import com.dorosoft.erp.catalog.application.product.ChangeSoldOutService;
import com.dorosoft.erp.catalog.application.product.CreateProductCommand;
import com.dorosoft.erp.catalog.application.product.CreateProductService;
import com.dorosoft.erp.catalog.application.product.ReplaceProductBasicInfoCommand;
import com.dorosoft.erp.catalog.application.product.ReplaceProductOptionsService;
import com.dorosoft.erp.catalog.application.product.UpdateProductService;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductOptionRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Catalog Revision이 정렬 변경뿐 아니라 Category·Product의 다른 모든 변경에서도 오르는지 검증한다
 * (API 명세.md 공통 계약 "변경 응답에는 최신 catalogRevision을 포함한다", 상품 등록·변경·옵션 변경·
 * 판매·품절 상태 변경 Sequence, 데이터 모델.md Transaction 경계). 공개 메뉴(MENU-06)의 ETag가 이 값을
 * 근거로 하므로, 정렬이 아닌 변경에서 Revision이 그대로면 오래된 메뉴가 캐시로 잘못 반환될 수 있다.
 */
@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(MySqlTestcontainersConfiguration.class)
@DisplayName("Catalog Revision 갱신 통합 테스트 - 정렬 외의 변경도 실제 MySQL에서 Revision을 올리는지 검증한다")
class CatalogRevisionAdvancesOnMutationIntegrationTest {

    @Autowired private CreateCategoryService createCategoryService;
    @Autowired private UpdateCategoryService updateCategoryService;
    @Autowired private CreateProductService createProductService;
    @Autowired private UpdateProductService updateProductService;
    @Autowired private ReplaceProductOptionsService replaceProductOptionsService;
    @Autowired private ChangeProductSalesPolicyService changeProductSalesPolicyService;
    @Autowired private ChangeSoldOutService changeSoldOutService;
    @Autowired private CatalogRevisionRepository catalogRevisionRepository;
    @Autowired private JdbcClient jdbcClient;

    private UUID catalogId;
    private UUID categoryId;
    private AuditContext auditContext;

    @BeforeEach
    void 테이블을_비우고_Catalog와_Category를_준비한다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
        categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        auditContext = CatalogIntegrationSupport.testAuditContext();
    }

    private long currentRevision() {
        return catalogRevisionRepository.findCurrent().orElseThrow().revision();
    }

    @Test
    @DisplayName("Category 생성은 Catalog Revision을 1 올린다")
    void categoryCreateAdvancesRevision() {
        long before = currentRevision();

        createCategoryService.create("차", null, auditContext);

        assertThat(currentRevision()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key의 Category 재요청(실제 변경 없음)은 Catalog Revision을 올리지 않는다")
    void categoryCreateIdempotentReplayDoesNotAdvanceRevision() {
        String key = "idem-revision-1";
        createCategoryService.create("차", key, auditContext);
        long before = currentRevision();

        createCategoryService.create("차", key, auditContext);

        assertThat(currentRevision()).isEqualTo(before);
    }

    @Test
    @DisplayName("Category 이름 변경은 Catalog Revision을 1 올린다")
    void categoryRenameAdvancesRevision() {
        Category created = createCategoryService.create("차", null, auditContext);
        long before = currentRevision();

        updateCategoryService.rename(created.categoryId(), "차·티", created.version(), auditContext);

        assertThat(currentRevision()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Product 생성은 Catalog Revision을 1 올린다")
    void productCreateAdvancesRevision() {
        long before = currentRevision();

        createProductService.create(basicCommand(null), auditContext);

        assertThat(currentRevision()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Product 기본 정보 교체는 Catalog Revision을 1 올린다")
    void productReplaceBasicInfoAdvancesRevision() {
        Product created = createProductService.create(basicCommand(null), auditContext);
        long before = currentRevision();

        updateProductService.replaceBasicInfo(
                created.productId(),
                new ReplaceProductBasicInfoCommand(categoryId, "카페라떼", null, 5000L, null, null, true, false),
                created.version(),
                auditContext);

        assertThat(currentRevision()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Product 옵션 전체 교체는 Catalog Revision을 1 올린다")
    void productReplaceOptionsAdvancesRevision() {
        Product created = createProductService.create(basicCommand(null), auditContext);
        long before = currentRevision();

        replaceProductOptionsService.replaceOptions(
                created.productId(), List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)), created.version(), auditContext);

        assertThat(currentRevision()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("판매·재고 관리 정책 변경은 Catalog Revision을 1 올린다")
    void productSalesPolicyChangeAdvancesRevision() {
        Product created = createProductService.create(basicCommand(null), auditContext);
        long before = currentRevision();

        changeProductSalesPolicyService.changePolicy(created.productId(), false, true, created.version(), auditContext);

        assertThat(currentRevision()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("값이 그대로인 판매 정책 재요청(No-op)은 Catalog Revision을 올리지 않는다")
    void productSalesPolicyNoOpDoesNotAdvanceRevision() {
        Product created = createProductService.create(basicCommand(null), auditContext);
        long before = currentRevision();

        changeProductSalesPolicyService.changePolicy(
                created.productId(), created.salesEnabled(), created.stockManaged(), created.version(), auditContext);

        assertThat(currentRevision()).isEqualTo(before);
    }

    @Test
    @DisplayName("수동 품절 변경은 Catalog Revision을 1 올린다")
    void productSoldOutChangeAdvancesRevision() {
        Product created = createProductService.create(basicCommand(null), auditContext);
        long before = currentRevision();

        changeSoldOutService.changeSoldOut(created.productId(), true, created.version(), auditContext);

        assertThat(currentRevision()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("값이 그대로인 품절 재요청(No-op)은 Catalog Revision을 올리지 않는다")
    void productSoldOutNoOpDoesNotAdvanceRevision() {
        Product created = createProductService.create(basicCommand(null), auditContext);
        long before = currentRevision();

        changeSoldOutService.changeSoldOut(created.productId(), created.soldOut(), created.version(), auditContext);

        assertThat(currentRevision()).isEqualTo(before);
    }

    private CreateProductCommand basicCommand(String idempotencyKey) {
        return new CreateProductCommand(categoryId, "아메리카노", "진한 에스프레소와 물", 4500L, null, null, true, false, idempotencyKey);
    }
}
