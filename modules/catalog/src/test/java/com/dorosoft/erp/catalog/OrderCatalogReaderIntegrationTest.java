package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.testsupport.MySqlTestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.api.OrderCatalogReader;
import com.dorosoft.erp.catalog.application.api.OrderCatalogSnapshot;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.product.ChangeProductSalesPolicyService;
import com.dorosoft.erp.catalog.application.product.ChangeSoldOutService;
import com.dorosoft.erp.catalog.application.product.CreateProductCommand;
import com.dorosoft.erp.catalog.application.product.CreateProductService;
import com.dorosoft.erp.catalog.application.product.ReplaceProductBasicInfoCommand;
import com.dorosoft.erp.catalog.application.product.ReplaceProductOptionsService;
import com.dorosoft.erp.catalog.application.product.UpdateProductService;
import com.dorosoft.erp.catalog.domain.orderability.OrderabilityReason;
import com.dorosoft.erp.catalog.domain.orderability.OrderabilityRejectedException;
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

@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(MySqlTestcontainersConfiguration.class)
@DisplayName("OrderCatalogReader 통합 테스트(MENU-07) - 실제 MySQL로 판매 가능성 판정 전체 순서를 검증한다")
class OrderCatalogReaderIntegrationTest {

    @Autowired private CreateProductService createProductService;
    @Autowired private UpdateProductService updateProductService;
    @Autowired private ChangeProductSalesPolicyService changeProductSalesPolicyService;
    @Autowired private ChangeSoldOutService changeSoldOutService;
    @Autowired private ReplaceProductOptionsService replaceProductOptionsService;
    @Autowired private OrderCatalogReader orderCatalogReader;
    @Autowired private CatalogRevisionRepository catalogRevisionRepository;
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

    @Test
    @DisplayName("옵션 없이도 Snapshot을 만든다")
    void resolvesSnapshotWithoutOptions() {
        Product product = createProduct();

        OrderCatalogSnapshot snapshot = orderCatalogReader.resolveForOrder(product.productId(), List.of());

        assertThat(snapshot.productId()).isEqualTo(product.productId());
        assertThat(snapshot.productName()).isEqualTo("아메리카노");
        assertThat(snapshot.baseUnitPrice()).isEqualTo(4500L);
        assertThat(snapshot.selectedOptions()).isEmpty();
        assertThat(snapshot.catalogRevision()).isEqualTo(catalogRevisionRepository.findCurrent().orElseThrow().revision());
    }

    @Test
    @DisplayName("선택한 옵션의 이름과 추가 금액을 Snapshot에 담는다")
    void resolvesSnapshotWithSelectedOptions() {
        Product product = createProduct();
        Product withOptions =
                replaceProductOptionsService.replaceOptions(
                        product.productId(),
                        List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)),
                        product.version(),
                        auditContext);
        UUID optionId = withOptions.options().get(0).optionId();

        OrderCatalogSnapshot snapshot = orderCatalogReader.resolveForOrder(product.productId(), List.of(optionId));

        assertThat(snapshot.selectedOptions()).hasSize(1);
        assertThat(snapshot.selectedOptions().get(0).optionId()).isEqualTo(optionId);
        assertThat(snapshot.selectedOptions().get(0).optionName()).isEqualTo("샷 추가");
        assertThat(snapshot.selectedOptions().get(0).additionalPrice()).isEqualTo(500L);
    }

    @Test
    @DisplayName("존재하지 않는 상품은 PRODUCT_NOT_FOUND")
    void throwsProductNotFoundForUnknownProduct() {
        assertThatThrownBy(() -> orderCatalogReader.resolveForOrder(UUID.randomUUID(), List.of()))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(OrderabilityRejectedException.class))
                .extracting(OrderabilityRejectedException::reason)
                .isEqualTo(OrderabilityReason.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("판매 비활성 상품은 PRODUCT_NOT_FOR_SALE")
    void throwsWhenNotForSale() {
        Product product = createProduct();
        changeProductSalesPolicyService.changePolicy(product.productId(), false, false, product.version(), auditContext);

        assertThatThrownBy(() -> orderCatalogReader.resolveForOrder(product.productId(), List.of()))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(OrderabilityRejectedException.class))
                .extracting(OrderabilityRejectedException::reason)
                .isEqualTo(OrderabilityReason.PRODUCT_NOT_FOR_SALE);
    }

    @Test
    @DisplayName("수동 품절 상품은 PRODUCT_SOLD_OUT")
    void throwsWhenSoldOut() {
        Product product = createProduct();
        changeSoldOutService.changeSoldOut(product.productId(), true, product.version(), auditContext);

        assertThatThrownBy(() -> orderCatalogReader.resolveForOrder(product.productId(), List.of()))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(OrderabilityRejectedException.class))
                .extracting(OrderabilityRejectedException::reason)
                .isEqualTo(OrderabilityReason.PRODUCT_SOLD_OUT);
    }

    @Test
    @DisplayName("옵션 ID를 중복 선택하면 DUPLICATE_OPTION_SELECTION")
    void throwsWhenDuplicateOptionSelected() {
        Product product = createProduct();
        Product withOptions =
                replaceProductOptionsService.replaceOptions(
                        product.productId(),
                        List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)),
                        product.version(),
                        auditContext);
        UUID optionId = withOptions.options().get(0).optionId();

        assertThatThrownBy(() -> orderCatalogReader.resolveForOrder(product.productId(), List.of(optionId, optionId)))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(OrderabilityRejectedException.class))
                .extracting(OrderabilityRejectedException::reason)
                .isEqualTo(OrderabilityReason.DUPLICATE_OPTION_SELECTION);
    }

    @Test
    @DisplayName("다른 상품의 옵션 ID를 선택하면 OPTION_NOT_FOUND")
    void throwsWhenOptionBelongsToDifferentProduct() {
        Product productA = createProduct();
        Product productAWithOption =
                replaceProductOptionsService.replaceOptions(
                        productA.productId(),
                        List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)),
                        productA.version(),
                        auditContext);
        UUID foreignOptionId = productAWithOption.options().get(0).optionId();

        Product productB =
                createProductService.create(
                        new CreateProductCommand(categoryId, "카페라떼", null, 5000L, null, null, true, false, null), auditContext);

        assertThatThrownBy(() -> orderCatalogReader.resolveForOrder(productB.productId(), List.of(foreignOptionId)))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(OrderabilityRejectedException.class))
                .extracting(OrderabilityRejectedException::reason)
                .isEqualTo(OrderabilityReason.OPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("비활성 옵션을 선택하면 OPTION_NOT_ORDERABLE")
    void throwsWhenOptionDisabled() {
        Product product = createProduct();
        Product withDisabledOption =
                replaceProductOptionsService.replaceOptions(
                        product.productId(),
                        List.of(new ProductOptionRequest(null, "단종 옵션", 500L, false)),
                        product.version(),
                        auditContext);
        UUID optionId = withDisabledOption.options().get(0).optionId();

        assertThatThrownBy(() -> orderCatalogReader.resolveForOrder(product.productId(), List.of(optionId)))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(OrderabilityRejectedException.class))
                .extracting(OrderabilityRejectedException::reason)
                .isEqualTo(OrderabilityReason.OPTION_NOT_ORDERABLE);
    }

    @Test
    @DisplayName("가격을 바꾼 뒤 조회하면 새 가격이 Snapshot에 반영된다")
    void reflectsPriceChangeAfterUpdate() {
        Product product = createProduct();
        assertThat(orderCatalogReader.resolveForOrder(product.productId(), List.of()).baseUnitPrice()).isEqualTo(4500L);

        updateProductService.replaceBasicInfo(
                product.productId(),
                new ReplaceProductBasicInfoCommand(categoryId, "아메리카노", null, 5000L, null, null, true, false),
                product.version(),
                auditContext);

        assertThat(orderCatalogReader.resolveForOrder(product.productId(), List.of()).baseUnitPrice()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("stockManaged 값 변경이 Snapshot에 그대로 반영된다")
    void reflectsStockManagedBranch() {
        Product product = createProduct();
        assertThat(orderCatalogReader.resolveForOrder(product.productId(), List.of()).stockManaged()).isFalse();

        changeProductSalesPolicyService.changePolicy(product.productId(), true, true, product.version(), auditContext);

        assertThat(orderCatalogReader.resolveForOrder(product.productId(), List.of()).stockManaged()).isTrue();
    }
}
