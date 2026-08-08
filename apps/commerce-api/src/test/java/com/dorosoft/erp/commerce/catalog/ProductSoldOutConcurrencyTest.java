package com.dorosoft.erp.commerce.catalog;

import static com.dorosoft.erp.commerce.support.CatalogTestFixtures.TENANT_A;
import static com.dorosoft.erp.commerce.support.CatalogTestFixtures.authenticate;
import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommandUseCase;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.ChangeSoldOutCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateCategoryCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogCommands.CreateProductCommand;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogErrorCode;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.CategoryView;
import com.dorosoft.erp.commerce.application.api.catalog.CatalogViews.ProductView;
import com.dorosoft.erp.commerce.application.api.security.ActorContextHolder;
import com.dorosoft.erp.commerce.application.api.security.ActorRole;
import com.dorosoft.erp.commerce.support.CatalogTestFixtures;
import com.dorosoft.erp.commerce.support.CommerceIntegrationTest;
import com.dorosoft.erp.platform.web.ProblemAwareException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SOFT-435: 같은 상품의 품절 상태를 동시에 바꿀 때 Lost Update가 생기지 않는지 실제 PostgreSQL에서 검증한다.
 */
@CommerceIntegrationTest
class ProductSoldOutConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private CatalogCommandUseCase catalogCommandUseCase;

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
    void onlyOneOfTheConcurrentSoldOutChangesWithTheSameVersionSucceeds() throws Exception {
        authenticate(TENANT_A, ActorRole.OWNER);
        CategoryView category = catalogCommandUseCase.createCategory(new CreateCategoryCommand("커피", 1, true));
        ProductView product = catalogCommandUseCase.createProduct(
                new CreateProductCommand(category.categoryId(), "아메리카노", null, 4500L, 1, true));
        long startVersion = product.version();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
                boolean soldOut = index % 2 == 0;
                Callable<Void> task = () -> {
                    // 각 요청은 독립된 Thread에서 자신의 Actor Context를 사용한다.
                    authenticate(TENANT_A, ActorRole.STAFF);
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        catalogCommandUseCase.changeSoldOut(
                                product.productId(), startVersion, new ChangeSoldOutCommand(soldOut));
                        success.incrementAndGet();
                    } catch (ProblemAwareException exception) {
                        if (exception.code() == CatalogErrorCode.CATALOG_VERSION_CONFLICT) {
                            conflict.incrementAndGet();
                        } else {
                            throw exception;
                        }
                    } finally {
                        ActorContextHolder.clear();
                    }
                    return null;
                };
                futures.add(executor.submit(task));
            }

            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // 같은 version을 조건으로 한 요청 중 정확히 하나만 성공한다.
        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(CONCURRENT_REQUESTS - 1);

        Long finalVersion = jdbcTemplate.queryForObject(
                "select version from product where id = ?", Long.class, product.productId());
        assertThat(finalVersion).isEqualTo(startVersion + 1);

        // 성공한 변경만 Audit Outbox에 남는다.
        Integer auditRows = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where aggregate_id = ? and event_type = 'PRODUCT_SOLD_OUT_CHANGED'",
                Integer.class,
                product.productId());
        assertThat(auditRows).isEqualTo(1);
    }

    @Test
    void sequentialSoldOutChangesWithTheCurrentVersionAllSucceed() {
        authenticate(TENANT_A, ActorRole.OWNER);
        CategoryView category = catalogCommandUseCase.createCategory(new CreateCategoryCommand("차", 1, true));
        ProductView product = catalogCommandUseCase.createProduct(
                new CreateProductCommand(category.categoryId(), "녹차", null, 4000L, 1, true));

        authenticate(TENANT_A, ActorRole.STAFF);
        ProductView first = catalogCommandUseCase.changeSoldOut(
                product.productId(), product.version(), new ChangeSoldOutCommand(true));
        ProductView second = catalogCommandUseCase.changeSoldOut(
                product.productId(), first.version(), new ChangeSoldOutCommand(false));

        assertThat(first.soldOut()).isTrue();
        assertThat(second.soldOut()).isFalse();
        assertThat(second.version()).isEqualTo(product.version() + 2);

        Boolean storedSoldOut = jdbcTemplate.queryForObject(
                "select sold_out from product where id = ?", Boolean.class, product.productId());
        assertThat(storedSoldOut).isFalse();

        Integer auditRows = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where event_type = 'PRODUCT_SOLD_OUT_CHANGED'", Integer.class);
        assertThat(auditRows).isEqualTo(2);
    }
}
