package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.product.CreateProductCommand;
import com.dorosoft.erp.catalog.application.product.CreateProductService;
import com.dorosoft.erp.catalog.application.product.ReplaceProductBasicInfoCommand;
import com.dorosoft.erp.catalog.application.product.UpdateProductService;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyKeyReusedException;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.application.category.CreateCategoryService;
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
 * Category·Product 동시 생성·유입, 표시 순서 부여와 생성 멱등성의 정합성을 실제 MySQL과 다중 스레드로
 * 검증한다. uk_category_display_order·uk_product_display_order UNIQUE 위반만 동시 생성·유입 충돌로
 * 판정해 OptimisticLockingFailureException(향후 Controller가 생기면 409 VERSION_CONFLICT로 매핑될
 * 도메인 신호)으로 거부하고, uk_*_idempotency_key 위반은 별개로 기존 결과 반환 또는
 * IdempotencyKeyReusedException으로 수렴하는지 확인한다.
 */
@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(MySqlTestcontainersConfiguration.class)
@DisplayName("Catalog 동시 생성·멱등성 정합성 통합 테스트 - 실제 MySQL·다중 스레드로 검증한다")
class CatalogConcurrentCreationIntegrationTest {

    @Autowired private CreateCategoryService createCategoryService;
    @Autowired private CreateProductService createProductService;
    @Autowired private UpdateProductService updateProductService;
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

    // --- 1. 동일 Catalog의 Category 동시 생성 ----------------------------------

    @Test
    @DisplayName("같은 Catalog에서 Category 2개를 동시에 생성하면 정확히 하나만 성공하고 나머지는 OptimisticLockingFailureException이다")
    void concurrentCategoryCreationInSameCatalogAllowsOnlyOneWinner() throws Exception {
        long revisionBefore = currentRevision();

        List<Object> results =
                runConcurrently(
                        List.of(
                                () -> createCategoryService.create("커피", null, auditContext),
                                () -> createCategoryService.create("차", null, auditContext)));

        assertExactlyOneWinnerOneConflict(results, Category.class);
        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "category")).isEqualTo(1);
        // 8. 표시 순서 충돌 시 Catalog Revision은 성공한 요청 하나만큼만(=1) 오른다.
        assertThat(currentRevision()).isEqualTo(revisionBefore + 1);
    }

    // --- 2. 동일 Category의 Product 동시 생성, 5. 공백이 있어도 MAX+1로 성공 -----------

    @Test
    @DisplayName("같은 Category에서 Product 2개를 동시에 생성하면 정확히 하나만 성공하고, 이후 순차 생성은 공백이 있어도 MAX+1로 성공한다")
    void concurrentProductCreationInSameCategoryAllowsOnlyOneWinnerThenGapIsHealed() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        long revisionBefore = currentRevision();

        List<Object> results =
                runConcurrently(
                        List.of(
                                () -> createProductService.create(basicCommand(categoryId, "아메리카노", null), auditContext),
                                () -> createProductService.create(basicCommand(categoryId, "카페라떼", null), auditContext)));

        assertExactlyOneWinnerOneConflict(results, Product.class);
        assertThat(currentRevision()).isEqualTo(revisionBefore + 1);

        // 패자의 실패로 display_order=0에는 승자만 있고 1은 비어 있다(공백). 순차 생성은 이 공백을
        // count()가 아니라 MAX+1로 건너뛰어 다음 값(1)에 성공해야 한다.
        Product third = createProductService.create(basicCommand(categoryId, "카페모카", null), auditContext);
        assertThat(third.displayOrder()).isEqualTo(1);
    }

    // --- 3. 서로 다른 Category의 Product 동시 생성 -------------------------------

    @Test
    @DisplayName("서로 다른 Category의 Product를 동시에 생성하면 둘 다 성공한다")
    void concurrentProductCreationInDifferentCategoriesBothSucceed() throws Exception {
        UUID coffeeCategoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID teaCategoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "차", 1);

        List<Object> results =
                runConcurrently(
                        List.of(
                                () -> createProductService.create(basicCommand(coffeeCategoryId, "아메리카노", null), auditContext),
                                () -> createProductService.create(basicCommand(teaCategoryId, "녹차", null), auditContext)));

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(Product.class));
        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "product")).isEqualTo(2);
    }

    // --- 4. Product 이동과 대상 Category Product 생성 경합 -------------------------

    @Test
    @DisplayName("Product를 다른 Category로 이동하는 요청과 그 대상 Category에 새 Product를 생성하는 요청이 경합하면 정확히 하나만 성공한다")
    void productMoveRacesWithCreationInTargetCategory() throws Exception {
        UUID sourceCategoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID targetCategoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "차", 1);
        Product toMove = createProductService.create(basicCommand(sourceCategoryId, "아메리카노", null), auditContext);

        List<Object> results =
                runConcurrently(
                        List.of(
                                () ->
                                        updateProductService.replaceBasicInfo(
                                                toMove.productId(),
                                                new ReplaceProductBasicInfoCommand(
                                                        targetCategoryId, toMove.name(), null, 4500L, null, null, true, false),
                                                toMove.version(),
                                                auditContext),
                                () -> createProductService.create(basicCommand(targetCategoryId, "녹차", null), auditContext)));

        assertExactlyOneWinnerOneConflict(results, Product.class);
        // 이동이 이기면 기존 Product 1개가 자리만 옮기고(count=1), 생성이 이기면 새 Product가 하나
        // 더 늘어난다(count=2). 어느 쪽이 이기든 "정확히 하나만 성공"이면 되므로 승자에 따라 기대값을
        // 맞춘다.
        boolean moveWon = results.get(0) instanceof Product;
        long expectedProductCount = moveWon ? 1 : 2;
        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "product")).isEqualTo(expectedProductCount);
    }

    // --- 6·7. 생성 멱등성 동시 요청 -------------------------------------------------

    @Test
    @DisplayName("같은 Idempotency-Key·같은 내용의 Category 생성 요청이 동시에 들어와도 한 번만 생성되고 같은 결과로 수렴한다")
    void concurrentIdempotentCategoryCreationWithSameBodyConvergesToOneResult() throws Exception {
        String key = "concurrent-idem-category";

        List<Object> results =
                runConcurrently(
                        List.of(
                                () -> createCategoryService.create("커피", key, auditContext),
                                () -> createCategoryService.create("커피", key, auditContext)));

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(Category.class));
        UUID firstId = ((Category) results.get(0)).categoryId();
        UUID secondId = ((Category) results.get(1)).categoryId();
        assertThat(secondId).isEqualTo(firstId);
        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "category")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key·다른 내용의 Product 생성 요청이 동시에 들어오면 하나는 성공하고 하나는 IdempotencyKeyReusedException이다")
    void concurrentIdempotentProductCreationWithDifferentBodyRejectsLoser() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        String key = "concurrent-idem-product";

        List<Object> results =
                runConcurrently(
                        List.of(
                                () -> createProductService.create(basicCommand(categoryId, "아메리카노", key), auditContext),
                                () -> createProductService.create(basicCommand(categoryId, "카페라떼", key), auditContext)));

        long successCount = results.stream().filter(Product.class::isInstance).count();
        long rejectedCount = results.stream().filter(IdempotencyKeyReusedException.class::isInstance).count();
        assertThat(successCount).isEqualTo(1);
        assertThat(rejectedCount).isEqualTo(1);
        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "product")).isEqualTo(1);
    }

    // --- 9. 다른 무결성 오류는 VERSION_CONFLICT로 잘못 매핑되지 않음 -------------------

    @Test
    @DisplayName("존재하지 않는 Media 참조(다른 무결성 오류)는 OptimisticLockingFailureException으로 바뀌지 않는다")
    void unrelatedIntegrityFailureIsNotMappedToOptimisticLockingFailure() {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        CreateProductCommand command =
                new CreateProductCommand(categoryId, "이름", null, 1000L, UUID.randomUUID(), null, true, false, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> createProductService.create(command, auditContext))
                .isNotInstanceOf(OptimisticLockingFailureException.class)
                .isInstanceOf(com.dorosoft.erp.catalog.domain.media.MediaNotFoundException.class);
    }

    private long currentRevision() {
        return catalogRevisionRepository.findCurrent().orElseThrow().revision();
    }

    private static CreateProductCommand basicCommand(UUID categoryId, String name, String idempotencyKey) {
        return new CreateProductCommand(categoryId, name, null, 4500L, null, null, true, false, idempotencyKey);
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
