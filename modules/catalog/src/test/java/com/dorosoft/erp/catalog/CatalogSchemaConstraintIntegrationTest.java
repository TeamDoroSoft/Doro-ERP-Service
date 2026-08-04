package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.testsupport.MySqlTestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(MySqlTestcontainersConfiguration.class)
@DisplayName("Catalog 스키마 제약 통합 테스트 - Migration이 선언한 FK/UNIQUE/CHECK 제약이 실제 MySQL에서 강제되는지 네이티브 SQL로 검증한다")
class CatalogSchemaConstraintIntegrationTest {

    @Autowired private JdbcClient jdbcClient;

    private UUID catalogId;
    private UUID categoryId;

    @BeforeEach
    void 테이블을_비우고_부모행을_만든다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
        categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "제약 검증용 카테고리", 0);
    }

    // --- FK 제약 ---------------------------------------------------------

    @Test
    @DisplayName("category는 존재하지 않는 catalog_id를 참조하면 저장에 실패한다")
    void category는_존재하지_않는_catalog를_참조할_수_없다() {
        UUID unknownCatalogId = UUID.randomUUID();

        assertThatThrownBy(
                        () -> CatalogIntegrationSupport.insertCategory(jdbcClient, unknownCatalogId, "고아 카테고리", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_category_catalog");
    }

    @Test
    @DisplayName("product는 존재하지 않는 category_id를 참조하면 저장에 실패한다")
    void product는_존재하지_않는_category를_참조할_수_없다() {
        UUID unknownCategoryId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                CatalogIntegrationSupport.insertProduct(
                                        jdbcClient, catalogId, unknownCategoryId, "고아 상품", 1000L, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_product_category");
    }

    @Test
    @DisplayName("product_option은 존재하지 않는 product_id를 참조하면 저장에 실패한다")
    void product_option은_존재하지_않는_product를_참조할_수_없다() {
        assertThatThrownBy(() -> insertProductOption(UUID.randomUUID(), "고아 옵션", 500L, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_product_option_product");
    }

    // --- UNIQUE 제약 ---------------------------------------------------------

    @Test
    @DisplayName("category는 (catalog_id, display_order)가 중복되면 저장에 실패한다")
    void category_표시순서_중복은_거부된다() {
        assertThatThrownBy(
                        () -> CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "중복 순서 카테고리", 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_category_display_order");
    }

    @Test
    @DisplayName("product는 (category_id, display_order)가 중복되면 저장에 실패한다")
    void product_표시순서_중복은_거부된다() {
        CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        assertThatThrownBy(
                        () ->
                                CatalogIntegrationSupport.insertProduct(
                                        jdbcClient, catalogId, categoryId, "카페라떼", 5000L, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_product_display_order");
    }

    @Test
    @DisplayName("product_option은 (product_id, display_order)가 중복되면 저장에 실패한다")
    void product_option_표시순서_중복은_거부된다() {
        UUID productId =
                CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);
        insertProductOption(productId, "샷 추가", 500L, 0);

        assertThatThrownBy(() -> insertProductOption(productId, "디카페인 변경", 700L, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_product_option_display_order");
    }

    @Test
    @DisplayName("product_media는 staging_object_key가 중복되면 저장에 실패한다")
    void product_media_staging_key_중복은_거부된다() {
        CatalogIntegrationSupport.insertReadyProductMedia(jdbcClient, catalogId, "media-1");

        assertThatThrownBy(() -> CatalogIntegrationSupport.insertReadyProductMedia(jdbcClient, catalogId, "media-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_product_media_staging_key");
    }

    // --- CHECK 제약 ----------------------------------------------------------

    @Test
    @DisplayName("category.display_order가 음수면 저장에 실패한다")
    void category_표시순서_음수는_거부된다() {
        assertThatThrownBy(() -> CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "음수 순서", -1))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_category_display_order");
    }

    @Test
    @DisplayName("product.base_price가 음수면 저장에 실패한다")
    void product_가격_음수는_거부된다() {
        assertThatThrownBy(
                        () ->
                                CatalogIntegrationSupport.insertProduct(
                                        jdbcClient, catalogId, categoryId, "음수 가격 상품", -100L, 0))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_product_base_price");
    }

    @Test
    @DisplayName("product_option.additional_price가 음수면 저장에 실패한다")
    void product_option_추가금액_음수는_거부된다() {
        UUID productId =
                CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        assertThatThrownBy(() -> insertProductOption(productId, "음수 옵션", -500L, 0))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_product_option_additional_price");
    }

    @Test
    @DisplayName("product_media.status가 PENDING/READY/REJECTED 외의 값이면 저장에 실패한다")
    void product_media_상태_위반은_거부된다() {
        assertThatThrownBy(() -> insertProductMediaWithStatus("UNKNOWN"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_product_media_status");
    }

    @Test
    @DisplayName("product_media는 published_object_key 없이 READY 상태로 저장할 수 없다")
    void product_media_READY는_published_key가_필수다() {
        UUID mediaId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                jdbcClient
                                        .sql(
                                                """
                                                INSERT INTO product_media
                                                    (media_id, catalog_id, staging_object_key, published_object_key,
                                                     object_etag, checksum_sha256, status, content_type, byte_size,
                                                     created_by, upload_expires_at, created_at, ready_at)
                                                VALUES (?, ?, ?, NULL, NULL, 'checksum-1', 'READY', 'image/webp', 1024,
                                                        ?, NOW(6), NOW(6), NULL)
                                                """)
                                        .param(CatalogIntegrationSupport.toBinary(mediaId))
                                        .param(CatalogIntegrationSupport.toBinary(catalogId))
                                        .param("tenants/t/catalog/staging/no-publish/source")
                                        .param(CatalogIntegrationSupport.toBinary(UUID.randomUUID()))
                                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_product_media_ready_requires_published");
    }

    // --- 허용되어야 하는 케이스 ------------------------------------------------

    @Test
    @DisplayName("PENDING 상태의 product_media는 published_object_key 없이 저장할 수 있다")
    void product_media_PENDING은_published_key_없이_허용된다() {
        UUID mediaId = UUID.randomUUID();

        assertThatCode(
                        () ->
                                jdbcClient
                                        .sql(
                                                """
                                                INSERT INTO product_media
                                                    (media_id, catalog_id, staging_object_key, published_object_key,
                                                     object_etag, checksum_sha256, status, content_type, byte_size,
                                                     created_by, upload_expires_at, created_at, ready_at)
                                                VALUES (?, ?, ?, NULL, NULL, 'checksum-1', 'PENDING', 'image/webp', 1024,
                                                        ?, NOW(6), NOW(6), NULL)
                                                """)
                                        .param(CatalogIntegrationSupport.toBinary(mediaId))
                                        .param(CatalogIntegrationSupport.toBinary(catalogId))
                                        .param("tenants/t/catalog/staging/pending-1/source")
                                        .param(CatalogIntegrationSupport.toBinary(UUID.randomUUID()))
                                        .update())
                .doesNotThrowAnyException();

        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "product_media")).isEqualTo(1L);
    }

    @Test
    @DisplayName("product는 media_id 없이 저장할 수 있다 (대표 이미지는 선택)")
    void product는_media_없이_허용된다() {
        assertThatCode(
                        () ->
                                CatalogIntegrationSupport.insertProduct(
                                        jdbcClient, catalogId, categoryId, "이미지 없는 상품", 3000L, 0))
                .doesNotThrowAnyException();

        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "product")).isEqualTo(1L);
    }

    // --- 네이티브 INSERT 헬퍼 --------------------------------------------------

    private void insertProductOption(UUID productId, String name, long additionalPrice, int displayOrder) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO product_option
                            (product_option_id, product_id, name, additional_price, enabled, display_order,
                             created_at, updated_at)
                        VALUES (?, ?, ?, ?, TRUE, ?, NOW(6), NOW(6))
                        """)
                .param(CatalogIntegrationSupport.toBinary(UUID.randomUUID()))
                .param(CatalogIntegrationSupport.toBinary(productId))
                .param(name)
                .param(additionalPrice)
                .param(displayOrder)
                .update();
    }

    private void insertProductMediaWithStatus(String status) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO product_media
                            (media_id, catalog_id, staging_object_key, published_object_key, object_etag,
                             checksum_sha256, status, content_type, byte_size, created_by, upload_expires_at,
                             created_at, ready_at)
                        VALUES (?, ?, ?, NULL, NULL, 'checksum-1', ?, 'image/webp', 1024, ?, NOW(6), NOW(6), NULL)
                        """)
                .param(CatalogIntegrationSupport.toBinary(UUID.randomUUID()))
                .param(CatalogIntegrationSupport.toBinary(catalogId))
                .param("tenants/t/catalog/staging/status-check/source")
                .param(status)
                .param(CatalogIntegrationSupport.toBinary(UUID.randomUUID()))
                .update();
    }
}
