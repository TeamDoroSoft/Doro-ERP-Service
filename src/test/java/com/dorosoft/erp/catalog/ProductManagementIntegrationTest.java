package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.product.CreateProductCommand;
import com.dorosoft.erp.catalog.application.product.CreateProductService;
import com.dorosoft.erp.catalog.application.product.ReplaceProductBasicInfoCommand;
import com.dorosoft.erp.catalog.application.product.ReplaceProductOptionsService;
import com.dorosoft.erp.catalog.application.product.UpdateProductService;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import com.dorosoft.erp.catalog.domain.media.MediaNotFoundException;
import com.dorosoft.erp.catalog.domain.product.IdempotencyKeyReusedException;
import com.dorosoft.erp.catalog.domain.product.InvalidPriceException;
import com.dorosoft.erp.catalog.domain.product.InvalidProductOptionsException;
import com.dorosoft.erp.catalog.domain.product.MediaNotReadyException;
import com.dorosoft.erp.catalog.domain.product.OptionOmissionNotAllowedException;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import com.dorosoft.erp.catalog.domain.product.ProductOptionRequest;
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
@Import(TestcontainersConfiguration.class)
@DisplayName("Product 관리 통합 테스트(FR-MENU-001, 002, 004) - 실제 MySQL로 멱등성·검증·낙관적 잠금을 확인한다")
class ProductManagementIntegrationTest {

    @Autowired private CreateProductService createProductService;
    @Autowired private UpdateProductService updateProductService;
    @Autowired private ReplaceProductOptionsService replaceProductOptionsService;
    @Autowired private JdbcClient jdbcClient;

    private UUID catalogId;
    private UUID categoryId;
    private UUID readyMediaId;

    @BeforeEach
    void 테이블을_비우고_Catalog와_Category를_준비한다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
        categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        readyMediaId = CatalogIntegrationSupport.insertReadyProductMedia(jdbcClient, catalogId, "americano");
    }

    private CreateProductCommand basicCommand(String idempotencyKey) {
        return new CreateProductCommand(categoryId, "아메리카노", "진한 에스프레소와 물", 4500L, null, null, true, false, idempotencyKey);
    }

    // --- 생성 ------------------------------------------------------------

    @Test
    @DisplayName("생성한 Product는 Category 안에서 마지막 순서를 받고 soldOut=false다")
    void createsProductAppendedAtEndOfCategory() {
        Product first = createProductService.create(basicCommand(null));
        Product second =
                createProductService.create(
                        new CreateProductCommand(categoryId, "카페라떼", null, 5000L, null, null, true, false, null));

        assertThat(first.displayOrder()).isZero();
        assertThat(second.displayOrder()).isEqualTo(1);
        assertThat(first.soldOut()).isFalse();
        assertThat(first.catalogId()).isEqualTo(catalogId);
    }

    @Test
    @DisplayName("존재하지 않는 Category로 생성하면 CategoryNotFoundException")
    void createWithNonExistentCategoryFails() {
        CreateProductCommand command =
                new CreateProductCommand(UUID.randomUUID(), "이름", null, 1000L, null, null, true, false, null);

        assertThatThrownBy(() -> createProductService.create(command)).isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("음수 가격으로 생성하면 InvalidPriceException")
    void createWithNegativePriceFails() {
        CreateProductCommand command =
                new CreateProductCommand(categoryId, "이름", null, -100L, null, null, true, false, null);

        assertThatThrownBy(() -> createProductService.create(command)).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    @DisplayName("존재하지 않는 Media를 연결하면 MediaNotFoundException")
    void createWithUnknownMediaFails() {
        CreateProductCommand command =
                new CreateProductCommand(categoryId, "이름", null, 1000L, UUID.randomUUID(), null, true, false, null);

        assertThatThrownBy(() -> createProductService.create(command)).isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    @DisplayName("READY가 아닌 Media를 연결하면 MediaNotReadyException")
    void createWithNotReadyMediaFails() {
        UUID pendingMediaId = insertPendingMedia();
        CreateProductCommand command =
                new CreateProductCommand(categoryId, "이름", null, 1000L, pendingMediaId, null, true, false, null);

        assertThatThrownBy(() -> createProductService.create(command)).isInstanceOf(MediaNotReadyException.class);
    }

    @Test
    @DisplayName("READY Media는 연결에 성공한다")
    void createWithReadyMediaSucceeds() {
        CreateProductCommand command =
                new CreateProductCommand(categoryId, "이름", null, 1000L, readyMediaId, "대체텍스트", true, false, null);

        Product created = createProductService.create(command);

        assertThat(created.mediaId()).isEqualTo(readyMediaId);
    }

    // --- 생성 멱등성 ---------------------------------------------------------

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 내용의 재요청은 기존 결과를 그대로 반환한다")
    void idempotentRetryWithSameBodyReturnsExistingProduct() {
        String key = "idem-1";
        Product first = createProductService.create(basicCommand(key));

        Product retried = createProductService.create(basicCommand(key));

        assertThat(retried.productId()).isEqualTo(first.productId());
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM product").query(Long.class).single();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 내용으로 재요청하면 IdempotencyKeyReusedException")
    void idempotentRetryWithDifferentBodyFails() {
        String key = "idem-2";
        createProductService.create(basicCommand(key));

        CreateProductCommand differentBody =
                new CreateProductCommand(categoryId, "다른 이름", null, 4500L, null, null, true, false, key);

        assertThatThrownBy(() -> createProductService.create(differentBody)).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    // --- 기본 정보 변경 ------------------------------------------------------

    @Test
    @DisplayName("기본 정보를 바꾸면 반영되고 soldOut·options는 유지된다")
    void updateReplacesBasicInfo() {
        Product created = createProductService.create(basicCommand(null));

        Product updated =
                updateProductService.replaceBasicInfo(
                        created.productId(),
                        new ReplaceProductBasicInfoCommand(categoryId, "카페라떼", "부드러운 우유 거품", 5000L, null, null, false, true),
                        created.version());

        assertThat(updated.name()).isEqualTo("카페라떼");
        assertThat(updated.basePrice()).isEqualTo(5000L);
        assertThat(updated.salesEnabled()).isFalse();
        assertThat(updated.soldOut()).isFalse();
        assertThat(updated.version()).isGreaterThan(created.version());
    }

    @Test
    @DisplayName("낡은 version으로 기본 정보를 바꾸면 OptimisticLockingFailureException")
    void updateWithStaleVersionFails() {
        Product created = createProductService.create(basicCommand(null));
        updateProductService.replaceBasicInfo(
                created.productId(),
                new ReplaceProductBasicInfoCommand(categoryId, "1차 수정", null, 4600L, null, null, true, false),
                created.version());

        ReplaceProductBasicInfoCommand staleUpdate =
                new ReplaceProductBasicInfoCommand(categoryId, "뒤늦은 수정", null, 4700L, null, null, true, false);
        assertThatThrownBy(() -> updateProductService.replaceBasicInfo(created.productId(), staleUpdate, created.version()))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("존재하지 않는 Product를 변경하면 ProductNotFoundException")
    void updateNonExistentProductFails() {
        ReplaceProductBasicInfoCommand command =
                new ReplaceProductBasicInfoCommand(categoryId, "이름", null, 1000L, null, null, true, false);

        assertThatThrownBy(() -> updateProductService.replaceBasicInfo(UUID.randomUUID(), command, 0L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("다른 Category로 이동하면 새 Category의 마지막 순서를 받는다")
    void updateMovingCategoryAppendsAtEnd() {
        UUID otherCategory = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "차", 1);
        CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, otherCategory, "기존 차 상품", 3000L, 0);
        Product created = createProductService.create(basicCommand(null));

        Product moved =
                updateProductService.replaceBasicInfo(
                        created.productId(),
                        new ReplaceProductBasicInfoCommand(otherCategory, "녹차", null, 4000L, null, null, true, false),
                        created.version());

        assertThat(moved.categoryId()).isEqualTo(otherCategory);
        assertThat(moved.displayOrder()).isEqualTo(1);
    }

    // --- 옵션 전체 교체 -------------------------------------------------------

    @Test
    @DisplayName("옵션을 바꾸면 Product version이 오른다(OneToMany 컬렉션만 바뀐 경우 포함)")
    void replaceOptionsBumpsProductVersion() {
        Product created = createProductService.create(basicCommand(null));

        Product withOptions =
                replaceProductOptionsService.replaceOptions(
                        created.productId(), List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)), created.version());

        assertThat(withOptions.version()).isGreaterThan(created.version());
        assertThat(withOptions.options()).hasSize(1);
        assertThat(withOptions.options().get(0).name()).isEqualTo("샷 추가");
    }

    @Test
    @DisplayName("기존 옵션 ID 누락은 거부되고, enabled=false 비활성화는 허용된다")
    void replaceOptionsOmissionRules() {
        Product created = createProductService.create(basicCommand(null));
        Product withOption =
                replaceProductOptionsService.replaceOptions(
                        created.productId(), List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)), created.version());
        UUID optionId = withOption.options().get(0).optionId();

        assertThatThrownBy(
                        () ->
                                replaceProductOptionsService.replaceOptions(
                                        created.productId(),
                                        List.of(new ProductOptionRequest(null, "새 옵션", 300L, true)),
                                        withOption.version()))
                .isInstanceOf(OptionOmissionNotAllowedException.class);

        Product disabled =
                replaceProductOptionsService.replaceOptions(
                        created.productId(),
                        List.of(new ProductOptionRequest(optionId, "샷 추가", 500L, false)),
                        withOption.version());
        assertThat(disabled.options().get(0).enabled()).isFalse();
    }

    @Test
    @DisplayName("다른 Product의 옵션 ID로 교체를 시도하면 InvalidProductOptionsException")
    void replaceOptionsWithForeignProductOptionFails() {
        Product productA = createProductService.create(basicCommand(null));
        Product productAWithOption =
                replaceProductOptionsService.replaceOptions(
                        productA.productId(), List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)), productA.version());
        UUID foreignOptionId = productAWithOption.options().get(0).optionId();

        Product productB =
                createProductService.create(
                        new CreateProductCommand(categoryId, "카페라떼", null, 5000L, null, null, true, false, null));

        assertThatThrownBy(
                        () ->
                                replaceProductOptionsService.replaceOptions(
                                        productB.productId(),
                                        List.of(new ProductOptionRequest(foreignOptionId, "다른 상품 옵션", 100L, true)),
                                        productB.version()))
                .isInstanceOf(InvalidProductOptionsException.class);
    }

    @Test
    @DisplayName("낡은 version으로 옵션을 바꾸면 OptimisticLockingFailureException")
    void replaceOptionsWithStaleVersionFails() {
        Product created = createProductService.create(basicCommand(null));
        replaceProductOptionsService.replaceOptions(
                created.productId(), List.of(new ProductOptionRequest(null, "샷 추가", 500L, true)), created.version());

        assertThatThrownBy(
                        () ->
                                replaceProductOptionsService.replaceOptions(
                                        created.productId(),
                                        List.of(new ProductOptionRequest(null, "또 다른 옵션", 200L, true)),
                                        created.version()))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    // --- 헬퍼 ----------------------------------------------------------------

    private UUID insertPendingMedia() {
        UUID mediaId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO product_media
                            (media_id, catalog_id, staging_object_key, checksum_sha256, status, content_type,
                             byte_size, created_by, upload_expires_at, created_at)
                        VALUES (?, ?, ?, 'checksum-1', 'PENDING', 'image/webp', 1024, ?, NOW(6), NOW(6))
                        """)
                .param(CatalogIntegrationSupport.toBinary(mediaId))
                .param(CatalogIntegrationSupport.toBinary(catalogId))
                .param("tenants/t/catalog/staging/pending-product-media/source")
                .param(CatalogIntegrationSupport.toBinary(UUID.randomUUID()))
                .update();
        return mediaId;
    }
}
