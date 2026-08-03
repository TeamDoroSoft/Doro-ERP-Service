package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName(
        "Catalog 스키마 Migration 통합 테스트"
                + " - 컨텍스트 기동 성공 자체가 Flyway Migration 적용과 ddl-auto=validate 통과(Migration과 Entity 매핑 일치)를 의미한다")
class CatalogSchemaMigrationIntegrationTest {

    private static final List<String> EXPECTED_TABLES =
            List.of("catalog_revision", "category", "product", "product_option", "product_media");

    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
    }

    @Test
    @DisplayName("Flyway가 현재 업체 Schema에 Catalog 모듈 테이블 5종을 모두 생성한다")
    void 테이블_5종이_모두_존재한다() {
        List<String> tables =
                jdbcClient
                        .sql(
                                """
                                SELECT TABLE_NAME
                                  FROM information_schema.TABLES
                                 WHERE TABLE_SCHEMA = DATABASE()
                                """)
                        .query(String.class)
                        .list();

        assertThat(tables).containsAll(EXPECTED_TABLES);
    }

    @Test
    @DisplayName("catalog_revision.catalog_id는 UUID BINARY(16) 매핑이다")
    void catalog_id는_BINARY_16이다() {
        assertThatColumnIsBinary16("catalog_revision", "catalog_id");
        assertThatColumnIsBinary16("category", "category_id");
        assertThatColumnIsBinary16("product", "product_id");
        assertThatColumnIsBinary16("product_option", "product_option_id");
        assertThatColumnIsBinary16("product_media", "media_id");
    }

    private void assertThatColumnIsBinary16(String table, String column) {
        String dataType =
                jdbcClient
                        .sql(
                                """
                                SELECT DATA_TYPE
                                  FROM information_schema.COLUMNS
                                 WHERE TABLE_SCHEMA = DATABASE()
                                   AND TABLE_NAME = ?
                                   AND COLUMN_NAME = ?
                                """)
                        .param(table)
                        .param(column)
                        .query(String.class)
                        .single();
        Long maxLength =
                jdbcClient
                        .sql(
                                """
                                SELECT CHARACTER_MAXIMUM_LENGTH
                                  FROM information_schema.COLUMNS
                                 WHERE TABLE_SCHEMA = DATABASE()
                                   AND TABLE_NAME = ?
                                   AND COLUMN_NAME = ?
                                """)
                        .param(table)
                        .param(column)
                        .query(Long.class)
                        .single();

        assertThat(dataType).as(table + "." + column).isEqualToIgnoringCase("binary");
        assertThat(maxLength).as(table + "." + column).isEqualTo(16L);
    }
}
