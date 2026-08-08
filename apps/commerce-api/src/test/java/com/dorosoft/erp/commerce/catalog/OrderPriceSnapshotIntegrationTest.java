package com.dorosoft.erp.commerce.catalog;

import static com.dorosoft.erp.commerce.support.CatalogTestFixtures.TENANT_A;
import static com.dorosoft.erp.commerce.support.CatalogTestFixtures.TENANT_B;
import static com.dorosoft.erp.commerce.support.CatalogTestFixtures.authenticate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommandUseCase;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.ChangeSoldOutCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.UpdateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogErrorCode;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderLineRequest;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogContracts.OrderQuote;
import com.dorosoft.erp.commerce.application.api.catalog.OrderCatalogQueryUseCase;
import com.dorosoft.erp.commerce.application.api.security.ActorRole;
import com.dorosoft.erp.commerce.support.CatalogTestFixtures;
import com.dorosoft.erp.commerce.support.CommerceIntegrationTest;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SOFT-437·SOFT-438: 실제 PostgreSQL에서 주문 가격 Snapshot의 불변성과 판매 가능 재검증을 확인한다.
 */
@CommerceIntegrationTest
class OrderPriceSnapshotIntegrationTest {

    @Autowired
    private CatalogCommandUseCase catalogCommandUseCase;

    @Autowired
    private OrderCatalogQueryUseCase orderCatalogQueryUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        CatalogTestFixtures.clear();
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from product");
        jdbcTemplate.update("delete from menu_category");
    }

    @Test
    void aPriceChangeDoesNotAlterASnapshotTakenBeforeIt() {
        authenticate(TENANT_A, ActorRole.OWNER);
        ProductView product = sellableProduct("아메리카노", 4500L);

        OrderQuote beforeChange = orderCatalogQueryUseCase.quoteOrderLines(
                List.of(new OrderLineRequest(product.productId(), 2)));
        assertThat(beforeChange.lines().get(0).product().unitPrice()).isEqualTo(4500L);
        assertThat(beforeChange.totalAmount()).isEqualTo(9000L);

        catalogCommandUseCase.updateProduct(product.productId(), product.version(),
                new UpdateProductCommand(null, null, null, 5000L, null, null));

        // 이미 확정된 Snapshot은 Catalog 변경 이후에도 그대로다 (FR-CATALOG-005).
        assertThat(beforeChange.lines().get(0).product().unitPrice()).isEqualTo(4500L);
        assertThat(beforeChange.lines().get(0).lineAmount()).isEqualTo(9000L);
        assertThat(beforeChange.totalAmount()).isEqualTo(9000L);

        // 새 주문은 변경된 현재 가격을 사용한다.
        OrderQuote afterChange = orderCatalogQueryUseCase.quoteOrderLines(
                List.of(new OrderLineRequest(product.productId(), 2)));
        assertThat(afterChange.lines().get(0).product().unitPrice()).isEqualTo(5000L);
        assertThat(afterChange.totalAmount()).isEqualTo(10000L);

        Long storedPrice = jdbcTemplate.queryForObject(
                "select price from product where id = ?", Long.class, product.productId());
        assertThat(storedPrice).isEqualTo(5000L);
    }

    @Test
    void quotingUsesTheServerPriceAndNeverAClientSuppliedOne() {
        authenticate(TENANT_A, ActorRole.OWNER);
        ProductView product = sellableProduct("아메리카노", 4500L);

        authenticate(TENANT_A, ActorRole.KIOSK_DEVICE);
        OrderQuote quote = orderCatalogQueryUseCase.quoteOrderLines(
                List.of(new OrderLineRequest(product.productId(), 1)));

        assertThat(quote.lines().get(0).product().unitPrice()).isEqualTo(4500L);
        assertThat(quote.lines().get(0).product().productName()).isEqualTo("아메리카노");
    }

    @Test
    void aProductSoldOutBetweenMenuAndOrderIsRejected() {
        authenticate(TENANT_A, ActorRole.OWNER);
        ProductView product = sellableProduct("아메리카노", 4500L);

        authenticate(TENANT_A, ActorRole.STAFF);
        catalogCommandUseCase.changeSoldOut(product.productId(), product.version(), new ChangeSoldOutCommand(true));

        assertThatThrownBy(() -> orderCatalogQueryUseCase.quoteOrderLines(
                List.of(new OrderLineRequest(product.productId(), 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(((ProblemAwareException) error).code())
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_SELLABLE));
    }

    @Test
    void aDeactivatedCategoryBlocksOrderingItsProducts() {
        authenticate(TENANT_A, ActorRole.OWNER);
        CategoryView category = catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 0, true));
        ProductView product = catalogCommandUseCase.createProduct(
                new CreateProductCommand(category.categoryId(), "아메리카노", null, 4500L, 0, true));

        catalogCommandUseCase.updateCategory(category.categoryId(), category.version(),
                new UpdateCategoryCommand(null, null, false));

        assertThatThrownBy(() -> orderCatalogQueryUseCase.quoteOrderLines(
                List.of(new OrderLineRequest(product.productId(), 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(((ProblemAwareException) error).code())
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_SELLABLE));
    }

    @Test
    void anotherTenantProductCannotBeQuoted() {
        authenticate(TENANT_A, ActorRole.OWNER);
        ProductView product = sellableProduct("아메리카노", 4500L);

        authenticate(TENANT_B, ActorRole.OWNER);
        assertThatThrownBy(() -> orderCatalogQueryUseCase.quoteOrderLines(
                List.of(new OrderLineRequest(product.productId(), 1))))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(((ProblemAwareException) error).code())
                        .isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void quotingWritesNothingToTheDatabase() {
        authenticate(TENANT_A, ActorRole.OWNER);
        ProductView product = sellableProduct("아메리카노", 4500L);
        jdbcTemplate.update("delete from outbox_event");
        Long versionBefore = jdbcTemplate.queryForObject(
                "select version from product where id = ?", Long.class, product.productId());

        orderCatalogQueryUseCase.quoteOrderLines(List.of(new OrderLineRequest(product.productId(), 3)));

        Long versionAfter = jdbcTemplate.queryForObject(
                "select version from product where id = ?", Long.class, product.productId());
        Integer outboxRows = jdbcTemplate.queryForObject("select count(*) from outbox_event", Integer.class);
        assertThat(versionAfter).isEqualTo(versionBefore);
        assertThat(outboxRows).isZero();
    }

    private ProductView sellableProduct(String name, long price) {
        CategoryView category = catalogCommandUseCase.createCategory(
                new CreateCategoryCommand(name + " 분류", 0, true));
        return catalogCommandUseCase.createProduct(
                new CreateProductCommand(category.categoryId(), name, null, price, 0, true));
    }
}
