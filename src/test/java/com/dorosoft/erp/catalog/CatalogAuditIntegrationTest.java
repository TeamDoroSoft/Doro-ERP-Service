package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.category.CreateCategoryService;
import com.dorosoft.erp.catalog.application.category.ReplaceCategoryOrderService;
import com.dorosoft.erp.catalog.application.category.ReplaceProductOrderInCategoryService;
import com.dorosoft.erp.catalog.application.category.UpdateCategoryService;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Catalog 감사 연동 통합 테스트(5.17, MENU-08). PlaceholderAuditWriter 대신 FakeAuditWriter를 등록해
 * Use Case가 실제로 제출한 AuditRecordCommand의 before·after 값이 민감정보·값 스키마 명세와 일치하는지,
 * 그리고 Media 값에 Object Key·ETag·Checksum·Presigned URL 같은 민감 필드가 절대 담기지 않는지 검증한다.
 */
@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import({TestcontainersConfiguration.class, CatalogAuditIntegrationTest.FakeAuditWriterConfig.class})
@DisplayName("Catalog 감사 연동 통합 테스트(5.17, MENU-08) - 실제 MySQL과 FakeAuditWriter로 Action별 값 스키마를 검증한다")
class CatalogAuditIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired private CreateCategoryService createCategoryService;
    @Autowired private UpdateCategoryService updateCategoryService;
    @Autowired private ReplaceCategoryOrderService replaceCategoryOrderService;
    @Autowired private ReplaceProductOrderInCategoryService replaceProductOrderInCategoryService;
    @Autowired private CreateProductService createProductService;
    @Autowired private UpdateProductService updateProductService;
    @Autowired private ReplaceProductOptionsService replaceProductOptionsService;
    @Autowired private ChangeProductSalesPolicyService changeProductSalesPolicyService;
    @Autowired private ChangeSoldOutService changeSoldOutService;
    @Autowired private FakeAuditWriter fakeAuditWriter;
    @Autowired private CatalogRevisionRepository catalogRevisionRepository;
    @Autowired private JdbcClient jdbcClient;

    private UUID catalogId;
    private AuditContext auditContext;

    @BeforeEach
    void 테이블을_비우고_Catalog를_초기화한다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
        auditContext = CatalogIntegrationSupport.testAuditContext();
        fakeAuditWriter.recorded.clear();
    }

    @Test
    @DisplayName("CATEGORY_CREATED는 name·version만 담는다")
    void categoryCreatedRecordsNameAndVersion() {
        Category created = createCategoryService.create("커피", null, auditContext);

        AuditRecordCommand command = fakeAuditWriter.last("CATEGORY_CREATED");
        assertThat(command.domain()).isEqualTo("CATALOG");
        assertThat(command.primaryTarget().targetId()).isEqualTo(created.categoryId().toString());
        Map<String, Object> after = readJson(command.afterValue());
        assertThat(after.keySet()).containsExactlyInAnyOrder("name", "version");
        assertThat(after.get("name")).isEqualTo("커피");
    }

    @Test
    @DisplayName("CATEGORY_UPDATED는 이름 변경 전후 값을 모두 담는다")
    void categoryUpdatedRecordsBeforeAndAfter() {
        Category created = createCategoryService.create("커피", null, auditContext);
        fakeAuditWriter.recorded.clear();

        updateCategoryService.rename(created.categoryId(), "커피·에스프레소", created.version(), auditContext);

        AuditRecordCommand command = fakeAuditWriter.last("CATEGORY_UPDATED");
        assertThat(readJson(command.beforeValue()).get("name")).isEqualTo("커피");
        assertThat(readJson(command.afterValue()).get("name")).isEqualTo("커피·에스프레소");
    }

    @Test
    @DisplayName("CATEGORY_ORDER_CHANGED는 categoryIds·catalogRevision을 담고 나머지 Category를 relatedTargets에 담는다")
    void categoryOrderChangedRecordsIdsAndRevision() {
        Category coffee = createCategoryService.create("커피", null, auditContext);
        Category tea = createCategoryService.create("차", null, auditContext);
        fakeAuditWriter.recorded.clear();
        long currentRevision = catalogRevisionRepository.findCurrent().orElseThrow().revision();

        replaceCategoryOrderService.replaceOrder(List.of(tea.categoryId(), coffee.categoryId()), currentRevision, auditContext);

        AuditRecordCommand command = fakeAuditWriter.last("CATEGORY_ORDER_CHANGED");
        assertThat(command.primaryTarget().targetId()).isEqualTo(tea.categoryId().toString());
        assertThat(command.relatedTargets()).extracting(t -> t.targetId()).containsExactly(coffee.categoryId().toString());
        Map<String, Object> after = readJson(command.afterValue());
        assertThat(after.keySet()).containsExactlyInAnyOrder("categoryIds", "catalogRevision");
    }

    @Test
    @DisplayName("PRODUCT_ORDER_CHANGED는 categoryId·productIds·catalogRevision을 담는다")
    void productOrderChangedRecordsCategoryAndProductIds() {
        Category category = createCategoryService.create("커피", null, auditContext);
        Product americano = createProductService.create(basicCommand(category.categoryId(), "아메리카노"), auditContext);
        Product latte = createProductService.create(basicCommand(category.categoryId(), "카페라떼"), auditContext);
        fakeAuditWriter.recorded.clear();
        long currentRevision = catalogRevisionRepository.findCurrent().orElseThrow().revision();

        replaceProductOrderInCategoryService.replaceOrder(
                category.categoryId(), List.of(latte.productId(), americano.productId()), currentRevision, auditContext);

        AuditRecordCommand command = fakeAuditWriter.last("PRODUCT_ORDER_CHANGED");
        Map<String, Object> after = readJson(command.afterValue());
        assertThat(after.keySet()).containsExactlyInAnyOrder("categoryId", "productIds", "catalogRevision");
    }

    @Test
    @DisplayName("PRODUCT_CREATED는 name·description·categoryId·version만 담는다")
    void productCreatedRecordsBasicFields() {
        Category category = createCategoryService.create("커피", null, auditContext);
        fakeAuditWriter.recorded.clear();

        Product created = createProductService.create(basicCommand(category.categoryId(), "아메리카노"), auditContext);

        AuditRecordCommand command = fakeAuditWriter.last("PRODUCT_CREATED");
        assertThat(command.primaryTarget().targetId()).isEqualTo(created.productId().toString());
        assertThat(command.relatedTargets()).extracting(t -> t.targetId()).containsExactly(category.categoryId().toString());
        Map<String, Object> after = readJson(command.afterValue());
        assertThat(after.keySet()).containsExactlyInAnyOrder("name", "description", "categoryId", "version");
    }

    @Test
    @DisplayName("이름만 바뀌면 PRODUCT_UPDATED만 기록되고 가격·이미지 Action은 기록되지 않는다")
    void updateWithOnlyNameChangeRecordsOnlyProductUpdated() {
        Category category = createCategoryService.create("커피", null, auditContext);
        Product created = createProductService.create(basicCommand(category.categoryId(), "아메리카노"), auditContext);
        fakeAuditWriter.recorded.clear();

        updateProductService.replaceBasicInfo(
                created.productId(),
                new ReplaceProductBasicInfoCommand(
                        category.categoryId(), "아메리카노 리저브", created.description(), created.basePrice().amount(), null, null, true, false),
                created.version(),
                auditContext);

        List<AuditRecordCommand> recorded = fakeAuditWriter.recorded;
        assertThat(recorded).extracting(AuditRecordCommand::action).containsExactly("PRODUCT_UPDATED");
    }

    @Test
    @DisplayName("가격과 대표 이미지가 함께 바뀌면 PRODUCT_UPDATED·PRODUCT_PRICE_CHANGED·PRODUCT_IMAGE_CHANGED가 같은 operationId로 기록된다")
    void updateWithPriceAndImageChangeRecordsAllThreeActions() {
        Category category = createCategoryService.create("커피", null, auditContext);
        UUID readyMediaId = CatalogIntegrationSupport.insertReadyProductMedia(jdbcClient, catalogId, "americano");
        Product created = createProductService.create(basicCommand(category.categoryId(), "아메리카노"), auditContext);
        fakeAuditWriter.recorded.clear();

        updateProductService.replaceBasicInfo(
                created.productId(),
                new ReplaceProductBasicInfoCommand(
                        category.categoryId(), created.name(), created.description(), 5500L, readyMediaId, "새 대체텍스트", true, false),
                created.version(),
                auditContext);

        List<AuditRecordCommand> recorded = fakeAuditWriter.recorded;
        assertThat(recorded).extracting(AuditRecordCommand::action)
                .containsExactly("PRODUCT_UPDATED", "PRODUCT_PRICE_CHANGED", "PRODUCT_IMAGE_CHANGED");
        assertThat(recorded).extracting(AuditRecordCommand::operationId).containsOnly(recorded.get(0).operationId());
        assertThat(recorded).extracting(AuditRecordCommand::eventSequence).containsExactly(0, 1, 2);

        AuditRecordCommand priceChanged = fakeAuditWriter.last("PRODUCT_PRICE_CHANGED");
        Map<String, Object> priceAfter = readJson(priceChanged.afterValue());
        assertThat(priceAfter.keySet()).containsExactlyInAnyOrder("basePrice", "currency", "version");
        assertThat(priceAfter.get("basePrice")).isEqualTo(5500);

        AuditRecordCommand imageChanged = fakeAuditWriter.last("PRODUCT_IMAGE_CHANGED");
        Map<String, Object> imageAfter = readJson(imageChanged.afterValue());
        assertThat(imageAfter.keySet()).containsExactlyInAnyOrder("mediaId", "imageAltText", "version");
        assertThat(imageAfter.get("mediaId")).isEqualTo(readyMediaId.toString());
        String rawJson = imageChanged.afterValue().toLowerCase() + imageChanged.beforeValue().toLowerCase();
        assertThat(rawJson)
                .doesNotContain("objectkey")
                .doesNotContain("etag")
                .doesNotContain("checksum")
                .doesNotContain("presign");
    }

    @Test
    @DisplayName("PRODUCT_OPTIONS_CHANGED는 options[] 배열과 version을 담는다")
    void productOptionsChangedRecordsOptionArray() {
        Category category = createCategoryService.create("커피", null, auditContext);
        Product created = createProductService.create(basicCommand(category.categoryId(), "아메리카노"), auditContext);
        fakeAuditWriter.recorded.clear();

        replaceProductOptionsService.replaceOptions(
                created.productId(), List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)), created.version(), auditContext);

        AuditRecordCommand command = fakeAuditWriter.last("PRODUCT_OPTIONS_CHANGED");
        Map<String, Object> after = readJson(command.afterValue());
        assertThat(after.keySet()).containsExactlyInAnyOrder("options", "version");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) after.get("options");
        assertThat(options).hasSize(1);
        assertThat(options.get(0).keySet())
                .containsExactlyInAnyOrder("optionId", "name", "additionalPrice", "enabled", "displayOrder");
    }

    @Test
    @DisplayName("PRODUCT_SALES_POLICY_CHANGED는 salesEnabled·stockManaged·version을 담고 값이 그대로면 기록되지 않는다")
    void productSalesPolicyChangedOnlyOnActualChange() {
        Category category = createCategoryService.create("커피", null, auditContext);
        Product created = createProductService.create(basicCommand(category.categoryId(), "아메리카노"), auditContext);
        fakeAuditWriter.recorded.clear();

        changeProductSalesPolicyService.changePolicy(
                created.productId(), created.salesEnabled(), created.stockManaged(), created.version(), auditContext);
        assertThat(fakeAuditWriter.recorded).isEmpty();

        Product updated =
                changeProductSalesPolicyService.changePolicy(created.productId(), false, true, created.version(), auditContext);

        AuditRecordCommand command = fakeAuditWriter.last("PRODUCT_SALES_POLICY_CHANGED");
        Map<String, Object> after = readJson(command.afterValue());
        assertThat(after.keySet()).containsExactlyInAnyOrder("salesEnabled", "stockManaged", "version");
        assertThat(after.get("salesEnabled")).isEqualTo(false);
        assertThat(updated.salesEnabled()).isFalse();
    }

    @Test
    @DisplayName("PRODUCT_SOLD_OUT_CHANGED는 soldOut·version을 담고 값이 그대로면 기록되지 않는다")
    void productSoldOutChangedOnlyOnActualChange() {
        Category category = createCategoryService.create("커피", null, auditContext);
        Product created = createProductService.create(basicCommand(category.categoryId(), "아메리카노"), auditContext);
        fakeAuditWriter.recorded.clear();

        changeSoldOutService.changeSoldOut(created.productId(), created.soldOut(), created.version(), auditContext);
        assertThat(fakeAuditWriter.recorded).isEmpty();

        changeSoldOutService.changeSoldOut(created.productId(), true, created.version(), auditContext);

        AuditRecordCommand command = fakeAuditWriter.last("PRODUCT_SOLD_OUT_CHANGED");
        Map<String, Object> after = readJson(command.afterValue());
        assertThat(after.keySet()).containsExactlyInAnyOrder("soldOut", "version");
        assertThat(after.get("soldOut")).isEqualTo(true);
    }

    private CreateProductCommand basicCommand(UUID categoryId, String name) {
        return new CreateProductCommand(categoryId, name, "설명", 4500L, null, null, true, false, null);
    }

    private static Map<String, Object> readJson(String json) {
        return OBJECT_MAPPER.readValue(json, Map.class);
    }

    @TestConfiguration
    static class FakeAuditWriterConfig {
        @Bean
        @Primary
        FakeAuditWriter fakeAuditWriter() {
            return new FakeAuditWriter();
        }
    }

    /** 실제 감사 모듈(feature 17) 없이 Use Case가 제출한 AuditRecordCommand를 그대로 수집하는 테스트 대역이다. */
    static class FakeAuditWriter implements AuditWriter {
        private final List<AuditRecordCommand> recorded = new ArrayList<>();

        @Override
        public com.dorosoft.erp.catalog.application.port.audit.AuditWriteResult record(
                AuditRecordCommand command, AuditContext context) {
            recorded.add(command);
            return new com.dorosoft.erp.catalog.application.port.audit.AuditWriteResult(
                    com.dorosoft.erp.catalog.application.port.audit.AuditWriteStatus.RECORDED,
                    UUID.randomUUID().toString(),
                    context.occurredAt());
        }

        AuditRecordCommand last(String action) {
            return recorded.stream()
                    .filter(c -> c.action().equals(action))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("기록되지 않은 Action: " + action + ", 실제 기록: "
                            + recorded.stream().map(AuditRecordCommand::action).collect(Collectors.toList())));
        }
    }
}
