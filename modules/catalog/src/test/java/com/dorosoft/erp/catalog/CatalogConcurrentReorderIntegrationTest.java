package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.catalog.application.category.ReplaceCategoryOrderService;
import com.dorosoft.erp.catalog.application.category.ReplaceProductOrderInCategoryService;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import com.dorosoft.erp.testsupport.MySqlTestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Category·Product 순서 변경(MENU-03·MENU-08)의 동시 요청 경합을 실제 MySQL과 다중 스레드로 검증한다
 * (MENU-10 인수 검증). {@link CatalogConcurrentCreationIntegrationTest}가 생성 경합을 다루는 것과 대칭으로,
 * 여기서는 같은 catalogRevision을 스냅샷한 두 순서 변경 요청이 동시에 들어왔을 때 정확히 하나만 성공하고
 * 나머지는 OptimisticLockingFailureException으로 거부되는지, DB에 남은 순서가 승자의 요청과 일치하는지
 * 확인한다.
 */
@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(MySqlTestcontainersConfiguration.class)
@DisplayName("Catalog 순서 변경 동시 요청 정합성 통합 테스트 - 실제 MySQL·다중 스레드로 검증한다")
class CatalogConcurrentReorderIntegrationTest {

    @Autowired private ReplaceCategoryOrderService replaceCategoryOrderService;
    @Autowired private ReplaceProductOrderInCategoryService replaceProductOrderInCategoryService;
    @Autowired private CatalogRevisionRepository catalogRevisionRepository;
    @Autowired private JdbcClient jdbcClient;

    private UUID catalogId;
    private AuditContext auditContext;

    @BeforeEach
    void 테이블을_비우고_Catalog를_초기화한다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
        auditContext = CatalogIntegrationSupport.testAuditContext();
    }

    @Test
    @DisplayName("같은 Revision을 스냅샷한 Category 순서 변경 2건이 동시에 들어오면 정확히 하나만 성공한다")
    void concurrentCategoryReorderAllowsOnlyOneWinner() throws Exception {
        UUID coffeeId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID teaId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "차", 1);
        long revisionBefore = currentRevision();

        List<Object> results =
                runConcurrently(
                        List.of(
                                () -> replaceCategoryOrderService.replaceOrder(List.of(teaId, coffeeId), revisionBefore, auditContext),
                                () -> replaceCategoryOrderService.replaceOrder(List.of(coffeeId, teaId), revisionBefore, auditContext)));

        assertExactlyOneWinnerOneConflict(results, CatalogRevision.class);
        assertThat(currentRevision()).isEqualTo(revisionBefore + 1);

        boolean firstWon = results.get(0) instanceof CatalogRevision;
        List<UUID> expectedOrder = firstWon ? List.of(teaId, coffeeId) : List.of(coffeeId, teaId);
        assertThat(orderedCategoryIds()).isEqualTo(expectedOrder);
    }

    @Test
    @DisplayName("같은 Revision을 스냅샷한 Product 순서 변경 2건이 동시에 들어오면 정확히 하나만 성공한다")
    void concurrentProductReorderAllowsOnlyOneWinner() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID americanoId = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);
        UUID latteId = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "카페라떼", 5000L, 1);
        long revisionBefore = currentRevision();

        List<Object> results =
                runConcurrently(
                        List.of(
                                () ->
                                        replaceProductOrderInCategoryService.replaceOrder(
                                                categoryId, List.of(latteId, americanoId), revisionBefore, auditContext),
                                () ->
                                        replaceProductOrderInCategoryService.replaceOrder(
                                                categoryId, List.of(americanoId, latteId), revisionBefore, auditContext)));

        assertExactlyOneWinnerOneConflict(results, CatalogRevision.class);
        assertThat(currentRevision()).isEqualTo(revisionBefore + 1);

        boolean firstWon = results.get(0) instanceof CatalogRevision;
        List<UUID> expectedOrder = firstWon ? List.of(latteId, americanoId) : List.of(americanoId, latteId);
        assertThat(orderedProductIds(categoryId)).isEqualTo(expectedOrder);
    }

    private long currentRevision() {
        return catalogRevisionRepository.findCurrent().orElseThrow().revision();
    }

    private List<UUID> orderedCategoryIds() {
        return jdbcClient
                .sql("SELECT category_id FROM category ORDER BY display_order")
                .query((rs, rowNum) -> CatalogIntegrationSupport.toUuid(rs.getBytes("category_id")))
                .list();
    }

    private List<UUID> orderedProductIds(UUID categoryId) {
        return jdbcClient
                .sql("SELECT product_id FROM product WHERE category_id = ? ORDER BY display_order")
                .param(CatalogIntegrationSupport.toBinary(categoryId))
                .query((rs, rowNum) -> CatalogIntegrationSupport.toUuid(rs.getBytes("product_id")))
                .list();
    }

    private static void assertExactlyOneWinnerOneConflict(List<Object> results, Class<?> successType) {
        long successCount = results.stream().filter(successType::isInstance).count();
        long conflictCount = results.stream().filter(OptimisticLockingFailureException.class::isInstance).count();
        assertThat(successCount).as("정확히 하나만 성공해야 한다: " + results).isEqualTo(1);
        assertThat(conflictCount).as("나머지 하나는 OptimisticLockingFailureException이어야 한다: " + results).isEqualTo(1);
    }

    /**
     * 전달한 작업들을 CyclicBarrier로 동시에 시작시켜 실제 경합을 유도한 뒤, 각 결과(성공 값 또는
     * 실패 원인 예외)를 제출 순서 그대로 반환한다.
     */
    private static List<Object> runConcurrently(List<Callable<?>> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier barrier = new CyclicBarrier(tasks.size());
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (Callable<?> task : tasks) {
                futures.add(
                        executor.submit(
                                () -> {
                                    barrier.await();
                                    return task.call();
                                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException ex) {
                    results.add(ex.getCause());
                }
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }
}
