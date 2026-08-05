package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.testsupport.MySqlTestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.category.CreateCategoryService;
import com.dorosoft.erp.catalog.application.category.ReplaceCategoryOrderService;
import com.dorosoft.erp.catalog.application.category.ReplaceProductOrderInCategoryService;
import com.dorosoft.erp.catalog.application.category.UpdateCategoryService;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.ProductOrderRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import com.dorosoft.erp.catalog.domain.category.InvalidDisplayOrderException;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyKeyReusedException;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.List;
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
@Import(MySqlTestcontainersConfiguration.class)
@DisplayName("Category 관리·정렬 통합 테스트(FR-MENU-003, FR-MENU-008) - 실제 MySQL로 원자성과 동시성 계약을 검증한다")
class CategoryManagementIntegrationTest {

    @Autowired private CreateCategoryService createCategoryService;
    @Autowired private UpdateCategoryService updateCategoryService;
    @Autowired private ReplaceCategoryOrderService replaceCategoryOrderService;
    @Autowired private ReplaceProductOrderInCategoryService replaceProductOrderInCategoryService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductOrderRepository productOrderRepository;
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

    // --- 생성 ------------------------------------------------------------

    @Test
    @DisplayName("생성한 Category는 순서대로 마지막 displayOrder를 받는다")
    void createsAppendAtEnd() {
        Category first = createCategoryService.create("커피", null, auditContext);
        Category second = createCategoryService.create("차", null, auditContext);
        Category third = createCategoryService.create("디저트", null, auditContext);

        assertThat(first.displayOrder()).isZero();
        assertThat(second.displayOrder()).isEqualTo(1);
        assertThat(third.displayOrder()).isEqualTo(2);
        assertThat(categoryRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("displayOrder에 공백이 있어도 Category는 현재 최댓값 다음 순서로 생성된다")
    void createsAfterMaximumDisplayOrderWhenGapExists() {
        CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "디저트", 2);

        Category created = createCategoryService.create("차", null, auditContext);

        assertThat(created.displayOrder()).isEqualTo(3);
    }

    // --- 생성 멱등성 ---------------------------------------------------------

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 내용의 재요청은 기존 결과를 그대로 반환한다")
    void idempotentRetryWithSameNameReturnsExistingCategory() {
        String key = "idem-category-1";
        Category first = createCategoryService.create("커피", key, auditContext);

        Category retried = createCategoryService.create("커피", key, auditContext);

        assertThat(retried.categoryId()).isEqualTo(first.categoryId());
        assertThat(categoryRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 내용으로 재요청하면 IdempotencyKeyReusedException")
    void idempotentRetryWithDifferentNameFails() {
        String key = "idem-category-2";
        createCategoryService.create("커피", key, auditContext);

        assertThatThrownBy(() -> createCategoryService.create("차", key, auditContext))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    // --- 이름 변경 ---------------------------------------------------------

    @Test
    @DisplayName("이름을 바꾸면 version이 오르고 displayOrder는 그대로다")
    void renameUpdatesNameAndBumpsVersion() {
        Category created = createCategoryService.create("커피", null, auditContext);

        Category renamed = updateCategoryService.rename(created.categoryId(), "커피·에스프레소", created.version(), auditContext);

        assertThat(renamed.name()).isEqualTo("커피·에스프레소");
        assertThat(renamed.version()).isGreaterThan(created.version());
        assertThat(renamed.displayOrder()).isEqualTo(created.displayOrder());
    }

    @Test
    @DisplayName("낡은 version으로 이름을 바꾸면 OptimisticLockingFailureException")
    void renameWithStaleVersionFails() {
        Category created = createCategoryService.create("커피", null, auditContext);
        updateCategoryService.rename(created.categoryId(), "1차 수정", created.version(), auditContext);

        assertThatThrownBy(
                        () -> updateCategoryService.rename(created.categoryId(), "뒤늦은 수정", created.version(), auditContext))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("존재하지 않는 Category를 변경하면 CategoryNotFoundException")
    void renameNonExistentCategoryFails() {
        assertThatThrownBy(() -> updateCategoryService.rename(UUID.randomUUID(), "이름", 0L, auditContext))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    // --- Category 정렬 -------------------------------------------------------

    @Test
    @DisplayName("Category 전체 순서를 교체하면 요청 배열 순서대로 0부터 재배정되고 Catalog Revision이 오른다")
    void replaceCategoryOrderSucceeds() {
        Category coffee = createCategoryService.create("커피", null, auditContext);
        Category tea = createCategoryService.create("차", null, auditContext);
        Category dessert = createCategoryService.create("디저트", null, auditContext);
        CatalogRevision before = currentRevision();

        CatalogRevision after =
                replaceCategoryOrderService.replaceOrder(
                        List.of(dessert.categoryId(), coffee.categoryId(), tea.categoryId()), before.revision(), auditContext);

        assertThat(after.revision()).isEqualTo(before.revision() + 1);
        List<Category> reordered = categoryRepository.findAll();
        assertThat(reordered).extracting(Category::categoryId)
                .containsExactly(dessert.categoryId(), coffee.categoryId(), tea.categoryId());
        assertThat(reordered).extracting(Category::displayOrder).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("Category 정렬 요청에 ID가 누락되면 거부되고 기존 순서를 유지한다")
    void replaceCategoryOrderWithMissingIdFails() {
        Category coffee = createCategoryService.create("커피", null, auditContext);
        Category tea = createCategoryService.create("차", null, auditContext);
        CatalogRevision before = currentRevision();

        assertThatThrownBy(
                        () ->
                                replaceCategoryOrderService.replaceOrder(
                                        List.of(coffee.categoryId()), before.revision(), auditContext))
                .isInstanceOf(InvalidDisplayOrderException.class);

        assertThat(categoryRepository.findAll()).extracting(Category::categoryId)
                .containsExactly(coffee.categoryId(), tea.categoryId());
        assertThat(currentRevision().revision()).isEqualTo(before.revision());
    }

    @Test
    @DisplayName("Category 정렬 요청에 ID가 중복되면 거부된다")
    void replaceCategoryOrderWithDuplicateIdFails() {
        Category coffee = createCategoryService.create("커피", null, auditContext);
        createCategoryService.create("차", null, auditContext);
        CatalogRevision before = currentRevision();

        assertThatThrownBy(
                        () ->
                                replaceCategoryOrderService.replaceOrder(
                                        List.of(coffee.categoryId(), coffee.categoryId()), before.revision(), auditContext))
                .isInstanceOf(InvalidDisplayOrderException.class);
    }

    @Test
    @DisplayName("낡은 Catalog Revision으로 정렬을 요청하면 OptimisticLockingFailureException이고 순서는 그대로다")
    void replaceCategoryOrderWithStaleRevisionFails() {
        Category coffee = createCategoryService.create("커피", null, auditContext);
        Category tea = createCategoryService.create("차", null, auditContext);
        long staleRevision = currentRevision().revision();
        replaceCategoryOrderService.replaceOrder(List.of(tea.categoryId(), coffee.categoryId()), staleRevision, auditContext);

        assertThatThrownBy(
                        () ->
                                replaceCategoryOrderService.replaceOrder(
                                        List.of(coffee.categoryId(), tea.categoryId()), staleRevision, auditContext))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(categoryRepository.findAll()).extracting(Category::categoryId)
                .containsExactly(tea.categoryId(), coffee.categoryId());
    }

    // --- Category 안의 Product 정렬 ------------------------------------------

    @Test
    @DisplayName("Category 안의 Product 순서를 교체하면 요청 순서대로 재배정되고 Catalog Revision이 오른다")
    void replaceProductOrderSucceeds() {
        Category category = createCategoryService.create("커피", null, auditContext);
        UUID americano = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, category.categoryId(), "아메리카노", 4500L, 0);
        UUID latte = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, category.categoryId(), "카페라떼", 5000L, 1);
        UUID mocha = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, category.categoryId(), "카페모카", 5500L, 2);
        CatalogRevision before = currentRevision();

        CatalogRevision after =
                replaceProductOrderInCategoryService.replaceOrder(
                        category.categoryId(), List.of(mocha, americano, latte), before.revision(), auditContext);

        assertThat(after.revision()).isEqualTo(before.revision() + 1);
        assertThat(productOrderRepository.findProductIdsByCategory(category.categoryId()))
                .containsExactly(mocha, americano, latte);
    }

    @Test
    @DisplayName("다른 Category 소속 Product가 섞이면 정렬을 거부한다")
    void replaceProductOrderWithForeignCategoryProductFails() {
        Category coffee = createCategoryService.create("커피", null, auditContext);
        Category tea = createCategoryService.create("차", null, auditContext);
        UUID americano = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, coffee.categoryId(), "아메리카노", 4500L, 0);
        UUID greenTea = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, tea.categoryId(), "녹차", 4000L, 0);
        CatalogRevision before = currentRevision();

        assertThatThrownBy(
                        () ->
                                replaceProductOrderInCategoryService.replaceOrder(
                                        coffee.categoryId(), List.of(americano, greenTea), before.revision(), auditContext))
                .isInstanceOf(InvalidDisplayOrderException.class);
    }

    @Test
    @DisplayName("존재하지 않는 Category의 Product 순서를 바꾸려 하면 CategoryNotFoundException")
    void replaceProductOrderForNonExistentCategoryFails() {
        CatalogRevision before = currentRevision();

        assertThatThrownBy(
                        () ->
                                replaceProductOrderInCategoryService.replaceOrder(
                                        UUID.randomUUID(), List.of(), before.revision(), auditContext))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    private CatalogRevision currentRevision() {
        return catalogRevisionRepository.findCurrent().orElseThrow();
    }
}
