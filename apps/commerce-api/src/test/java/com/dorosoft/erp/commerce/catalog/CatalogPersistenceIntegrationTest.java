package com.dorosoft.erp.commerce.catalog;

import static com.dorosoft.erp.commerce.support.CatalogTestFixtures.STORE_A;
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
import com.dorosoft.erp.commerce.application.api.catalog.CatalogQueryUseCase;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import com.dorosoft.erp.commerce.application.api.security.ActorRole;
import com.dorosoft.erp.commerce.support.CatalogTestFixtures;
import com.dorosoft.erp.commerce.support.CommerceIntegrationTest;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SOFT-435: 실제 PostgreSQL에서 Flyway Migration, Tenant Scope, Constraint와 Audit Outbox 원자성 검증.
 */
@CommerceIntegrationTest
class CatalogPersistenceIntegrationTest {

    @Autowired
    private CatalogCommandUseCase catalogCommandUseCase;

    @Autowired
    private CatalogQueryUseCase catalogQueryUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void tearDown() {
        CatalogTestFixtures.clear();
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from product");
        jdbcTemplate.update("delete from menu_category");
    }

    @Test
    void flywayCreatesTheOwnedCatalogSchema() {
        Integer migrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(migrations).isGreaterThanOrEqualTo(1);

        Integer tables = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.tables
                 where table_schema = 'public'
                   and table_name in ('menu_category', 'product', 'outbox_event')
                """,
                Integer.class);
        assertThat(tables).isEqualTo(3);
    }

    @Test
    void negativePriceIsRejectedByTheDatabaseCheckConstraint() throws SQLException {
        authenticate(TENANT_A, ActorRole.OWNER);
        CategoryView category = catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));

        // Application을 우회해도 음수 가격은 저장될 수 없다.
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate(
                    "insert into product (id, tenant_id, category_id, name, price, sold_out, status,"
                            + " display_order, version, created_at, updated_at) values ('"
                            + UUID.randomUUID() + "', '" + TENANT_A + "', '" + category.categoryId()
                            + "', '음수가격', -1, false, 'ACTIVE', 0, 0, now(), now())"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void duplicatedCategoryNameIsRejectedByTheUniqueIndex() {
        authenticate(TENANT_A, ActorRole.OWNER);
        catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));

        assertThatThrownBy(() -> catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 2, true)))
                .isInstanceOf(ProblemAwareException.class);

        Integer stored = jdbcTemplate.queryForObject(
                "select count(*) from menu_category where tenant_id = ?", Integer.class, TENANT_A);
        assertThat(stored).isEqualTo(1);
    }

    @Test
    void sameCategoryNameIsAllowedInDifferentTenants() {
        authenticate(TENANT_A, ActorRole.OWNER);
        catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));

        authenticate(TENANT_B, ActorRole.OWNER);
        CategoryView other = catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));

        assertThat(other.name()).isEqualTo("커피");
    }

    @Test
    void otherTenantCannotReadOrUpdateCategory() {
        authenticate(TENANT_B, ActorRole.OWNER);
        CategoryView foreign = catalogCommandUseCase.createCategory(new CreateCategoryCommand("디저트", 1, true));

        authenticate(TENANT_A, ActorRole.OWNER);
        assertThat(catalogQueryUseCase.loadManagedCategories()).isEmpty();
        assertThatThrownBy(() -> catalogCommandUseCase.updateCategory(
                foreign.categoryId(), foreign.version(), new UpdateCategoryCommand("변경", null, null)))
                .isInstanceOf(ProblemAwareException.class);

        String storedName = jdbcTemplate.queryForObject(
                "select name from menu_category where id = ?", String.class, foreign.categoryId());
        assertThat(storedName).isEqualTo("디저트");
    }

    @Test
    void deactivationHidesTheMenuWithoutDeletingRows() {
        authenticate(TENANT_A, ActorRole.OWNER);
        CategoryView category = catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));
        ProductView product = catalogCommandUseCase.createProduct(
                new CreateProductCommand(category.categoryId(), "아메리카노", "기본", 4500L, 1, true));

        catalogCommandUseCase.updateProduct(
                product.productId(), product.version(),
                new UpdateProductCommand(null, null, null, null, null, false));

        assertThat(catalogQueryUseCase.loadSalesMenu().categories().get(0).products()).isEmpty();
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from product where id = ?", Integer.class, product.productId());
        assertThat(rows).isEqualTo(1);

        catalogCommandUseCase.updateCategory(
                category.categoryId(), category.version(), new UpdateCategoryCommand(null, null, false));
        assertThat(catalogQueryUseCase.loadSalesMenu().categories()).isEmpty();
        Integer categoryRows = jdbcTemplate.queryForObject(
                "select count(*) from menu_category where id = ?", Integer.class, category.categoryId());
        assertThat(categoryRows).isEqualTo(1);
    }

    @Test
    void businessChangeAndAuditOutboxShareOneLocalTransaction() {
        authenticate(TENANT_A, STORE_A, ActorRole.OWNER);
        CategoryView category = catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));
        ProductView product = catalogCommandUseCase.createProduct(
                new CreateProductCommand(category.categoryId(), "아메리카노", null, 4500L, 1, true));

        catalogCommandUseCase.updateProduct(
                product.productId(), product.version(),
                new UpdateProductCommand(null, null, null, 5000L, null, null));

        assertThat(auditActions(product.productId()))
                .contains("PRODUCT_CHANGED", "PRODUCT_PRICE_CHANGED");
        assertThat(auditActions(category.categoryId())).contains("CATEGORY_CHANGED");

        String destination = jdbcTemplate.queryForObject(
                "select distinct destination from outbox_event", String.class);
        assertThat(destination).isEqualTo("audit-events.fifo");
    }

    @Test
    void rejectedChangeLeavesNoBusinessRowAndNoAuditOutbox() {
        authenticate(TENANT_A, ActorRole.OWNER);
        CategoryView category = catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));
        ProductView product = catalogCommandUseCase.createProduct(
                new CreateProductCommand(category.categoryId(), "아메리카노", null, 4500L, 1, true));
        jdbcTemplate.update("delete from outbox_event");

        assertThatThrownBy(() -> catalogCommandUseCase.updateProduct(
                product.productId(), product.version(),
                new UpdateProductCommand(null, null, null, -1L, null, null)))
                .isInstanceOf(ProblemAwareException.class);

        Long storedPrice = jdbcTemplate.queryForObject(
                "select price from product where id = ?", Long.class, product.productId());
        assertThat(storedPrice).isEqualTo(4500L);
        Integer outboxRows = jdbcTemplate.queryForObject("select count(*) from outbox_event", Integer.class);
        assertThat(outboxRows).isZero();
    }

    @Test
    void staffCannotChangePriceButCanChangeSoldOut() {
        authenticate(TENANT_A, ActorRole.OWNER);
        CategoryView category = catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));
        ProductView product = catalogCommandUseCase.createProduct(
                new CreateProductCommand(category.categoryId(), "아메리카노", null, 4500L, 1, true));

        authenticate(TENANT_A, ActorRole.STAFF);
        assertThatThrownBy(() -> catalogCommandUseCase.updateProduct(
                product.productId(), product.version(),
                new UpdateProductCommand(null, null, null, 100L, null, null)))
                .isInstanceOf(ProblemAwareException.class)
                .satisfies(error -> assertThat(((ProblemAwareException) error).code())
                        .isEqualTo(CatalogErrorCode.FORBIDDEN));

        ProductView soldOut = catalogCommandUseCase.changeSoldOut(
                product.productId(), product.version(), new ChangeSoldOutCommand(true));

        assertThat(soldOut.soldOut()).isTrue();
        assertThat(soldOut.version()).isEqualTo(product.version() + 1);
        Long storedPrice = jdbcTemplate.queryForObject(
                "select price from product where id = ?", Long.class, product.productId());
        assertThat(storedPrice).isEqualTo(4500L);
    }

    private java.util.List<String> auditActions(UUID aggregateId) {
        return jdbcTemplate.queryForList(
                "select event_type from outbox_event where aggregate_id = ? order by occurred_at",
                String.class,
                aggregateId);
    }
}
