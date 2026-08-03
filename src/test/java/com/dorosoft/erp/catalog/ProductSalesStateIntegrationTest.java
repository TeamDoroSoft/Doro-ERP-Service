package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.api.StockManagementPolicyReader;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.product.ChangeProductSalesPolicyService;
import com.dorosoft.erp.catalog.application.product.ChangeSoldOutService;
import com.dorosoft.erp.catalog.application.product.CreateProductCommand;
import com.dorosoft.erp.catalog.application.product.CreateProductService;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Product 판매·품절·재고 관리 설정 통합 테스트(FR-MENU-005~007) - 실제 MySQL로 허용·거부·동시성을 검증한다")
class ProductSalesStateIntegrationTest {

    @Autowired private CreateProductService createProductService;
    @Autowired private ChangeProductSalesPolicyService changeProductSalesPolicyService;
    @Autowired private ChangeSoldOutService changeSoldOutService;
    @Autowired private StockManagementPolicyReader stockManagementPolicyReader;
    @Autowired private JdbcClient jdbcClient;

    private UUID categoryId;
    private AuditContext auditContext;

    @BeforeEach
    void 테이블을_비우고_Category를_준비한다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        UUID catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
        categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        auditContext = CatalogIntegrationSupport.testAuditContext();
    }

    private Product createProduct() {
        return createProductService.create(
                new CreateProductCommand(categoryId, "아메리카노", null, 4500L, null, null, true, false, null), auditContext);
    }

    // --- 판매 활성화·재고 관리 여부 -------------------------------------------------

    @Test
    @DisplayName("판매·재고 관리 정책을 바꾸면 반영되고 version이 오른다")
    void changeSalesPolicySucceeds() {
        Product created = createProduct();

        Product updated =
                changeProductSalesPolicyService.changePolicy(created.productId(), false, true, created.version(), auditContext);

        assertThat(updated.salesEnabled()).isFalse();
        assertThat(updated.stockManaged()).isTrue();
        assertThat(updated.version()).isGreaterThan(created.version());
    }

    @Test
    @DisplayName("현재와 같은 값으로 재요청하면 version을 그대로 유지한다")
    void changeSalesPolicyNoOpWhenUnchanged() {
        Product created = createProduct();

        Product result =
                changeProductSalesPolicyService.changePolicy(
                        created.productId(), created.salesEnabled(), created.stockManaged(), created.version(), auditContext);

        assertThat(result.version()).isEqualTo(created.version());
    }

    @Test
    @DisplayName("낡은 version으로 정책을 바꾸면 OptimisticLockingFailureException")
    void changeSalesPolicyWithStaleVersionFails() {
        Product created = createProduct();
        changeProductSalesPolicyService.changePolicy(created.productId(), false, false, created.version(), auditContext);

        assertThatThrownBy(
                        () ->
                                changeProductSalesPolicyService.changePolicy(
                                        created.productId(), true, true, created.version(), auditContext))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("존재하지 않는 Product의 정책을 바꾸면 ProductNotFoundException")
    void changeSalesPolicyForNonExistentProductFails() {
        assertThatThrownBy(
                        () -> changeProductSalesPolicyService.changePolicy(UUID.randomUUID(), true, false, 0L, auditContext))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // --- 수동 품절 ------------------------------------------------------------

    @Test
    @DisplayName("품절 상태를 바꾸면 반영되고 version이 오른다")
    void changeSoldOutSucceeds() {
        Product created = createProduct();

        Product updated = changeSoldOutService.changeSoldOut(created.productId(), true, created.version(), auditContext);

        assertThat(updated.soldOut()).isTrue();
        assertThat(updated.version()).isGreaterThan(created.version());
    }

    @Test
    @DisplayName("같은 값으로 재요청하면 낡은 version이어도 성공하고 version은 그대로다")
    void changeSoldOutSameValueSkipsVersionCheck() {
        Product created = createProduct();
        long staleVersion = created.version() + 999;

        Product result = changeSoldOutService.changeSoldOut(created.productId(), created.soldOut(), staleVersion, auditContext);

        assertThat(result.soldOut()).isEqualTo(created.soldOut());
        assertThat(result.version()).isEqualTo(created.version());
    }

    @Test
    @DisplayName("값이 실제로 바뀌는데 낡은 version이면 OptimisticLockingFailureException")
    void changeSoldOutWithStaleVersionFailsWhenValueChanges() {
        Product created = createProduct();
        changeSoldOutService.changeSoldOut(created.productId(), true, created.version(), auditContext);

        assertThatThrownBy(() -> changeSoldOutService.changeSoldOut(created.productId(), false, created.version(), auditContext))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("존재하지 않는 Product를 품절 처리하면 ProductNotFoundException")
    void changeSoldOutForNonExistentProductFails() {
        assertThatThrownBy(() -> changeSoldOutService.changeSoldOut(UUID.randomUUID(), true, 0L, auditContext))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // --- StockManagementPolicyReader(공개 계약) ---------------------------------

    @Test
    @DisplayName("StockManagementPolicyReader는 현재 stockManaged 값을 반환한다")
    void stockManagementPolicyReaderReflectsCurrentValue() {
        Product created = createProduct();
        assertThat(stockManagementPolicyReader.isStockManaged(created.productId())).isFalse();

        changeProductSalesPolicyService.changePolicy(
                created.productId(), created.salesEnabled(), true, created.version(), auditContext);

        assertThat(stockManagementPolicyReader.isStockManaged(created.productId())).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 Product를 조회하면 ProductNotFoundException")
    void stockManagementPolicyReaderThrowsForUnknownProduct() {
        assertThatThrownBy(() -> stockManagementPolicyReader.isStockManaged(UUID.randomUUID()))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
