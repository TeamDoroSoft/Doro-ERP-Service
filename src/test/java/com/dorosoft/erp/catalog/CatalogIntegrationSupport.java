package com.dorosoft.erp.catalog;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Catalog 통합 테스트 공통 유틸.
 *
 * <p>Spring 어노테이션을 전혀 갖지 않는 순수 유틸이다. 통합 테스트 클래스는 각자 동일한 {@code @SpringBootTest} /
 * {@code @Import} 어노테이션을 직접 선언해 하나의 컨텍스트(=하나의 컨테이너 쌍)를 공유한다.
 */
final class CatalogIntegrationSupport {

    /** FK 자식 → 부모 순서. flyway_schema_history는 절대 건드리지 않는다. */
    private static final List<String> DELETE_ORDER =
            List.of("product_option", "product", "category", "product_media", "catalog_revision");

    private CatalogIntegrationSupport() {}

    /** 테스트 간 격리를 위해 Catalog 모듈 테이블을 모두 비운다. */
    static void cleanCatalogTables(JdbcClient jdbcClient) {
        for (String table : DELETE_ORDER) {
            jdbcClient.sql("DELETE FROM " + table).update();
        }
    }

    /** UUID를 Hibernate 6 기본 매핑과 동일한 BINARY(16) 바이트 배열로 변환한다. */
    static byte[] toBinary(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    static UUID toUuid(byte[] binary) {
        ByteBuffer buffer = ByteBuffer.wrap(binary);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    static UUID insertCatalogRevision(JdbcClient jdbcClient) {
        UUID catalogId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO catalog_revision (catalog_id, revision, updated_at)
                        VALUES (?, 0, NOW(6))
                        """)
                .param(toBinary(catalogId))
                .update();
        return catalogId;
    }

    static UUID insertCategory(JdbcClient jdbcClient, UUID catalogId, String name, int displayOrder) {
        UUID categoryId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO category
                            (category_id, catalog_id, name, display_order, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 0, NOW(6), NOW(6))
                        """)
                .param(toBinary(categoryId))
                .param(toBinary(catalogId))
                .param(name)
                .param(displayOrder)
                .update();
        return categoryId;
    }

    static UUID insertReadyProductMedia(JdbcClient jdbcClient, UUID catalogId, String keySuffix) {
        UUID mediaId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO product_media
                            (media_id, catalog_id, staging_object_key, published_object_key, object_etag,
                             checksum_sha256, status, content_type, byte_size, created_by, upload_expires_at,
                             created_at, ready_at)
                        VALUES (?, ?, ?, ?, 'etag-1', 'checksum-1', 'READY', 'image/webp', 1024, ?, NOW(6), NOW(6), NOW(6))
                        """)
                .param(toBinary(mediaId))
                .param(toBinary(catalogId))
                .param("tenants/t/catalog/staging/" + keySuffix + "/source")
                .param("tenants/t/catalog/public/" + keySuffix + "/hash.webp")
                .param(toBinary(UUID.randomUUID()))
                .update();
        return mediaId;
    }

    static UUID insertProduct(
            JdbcClient jdbcClient,
            UUID catalogId,
            UUID categoryId,
            String name,
            long basePrice,
            int displayOrder) {
        UUID productId = UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO product
                            (product_id, catalog_id, category_id, name, base_price, sales_enabled,
                             sold_out, stock_managed, display_order, version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, TRUE, FALSE, FALSE, ?, 0, NOW(6), NOW(6))
                        """)
                .param(toBinary(productId))
                .param(toBinary(catalogId))
                .param(toBinary(categoryId))
                .param(name)
                .param(basePrice)
                .param(displayOrder)
                .update();
        return productId;
    }

    static long countOf(JdbcClient jdbcClient, String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }
}
