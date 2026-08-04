package com.dorosoft.erp.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "doro.store.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Store 스키마 Migration 통합 테스트 - 컨텍스트 기동에 성공했다는 것 자체가 Flyway V1 적용과 ddl-auto=validate 통과(Migration과 Entity 매핑 일치)를 의미한다")
class StoreSchemaMigrationIntegrationTest {

    private static final List<String> EXPECTED_TABLES =
            List.of(
                    "store_profile",
                    "business_hour",
                    "regular_closed_day",
                    "temporary_closure",
                    "service_window",
                    "feature_setting",
                    "notification_event_setting");

    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
    }

    @Test
    @DisplayName("Flyway가 현재 업체 Schema에 Store 모듈 테이블 7종을 모두 생성한다")
    void 테이블_7종이_모두_존재한다() {
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
    @DisplayName("store_profile.store_id는 UUID BINARY(16) 매핑이다")
    void store_id는_BINARY_16이다() {
        String dataType =
                jdbcClient
                        .sql(
                                """
                                SELECT DATA_TYPE
                                  FROM information_schema.COLUMNS
                                 WHERE TABLE_SCHEMA = DATABASE()
                                   AND TABLE_NAME = 'store_profile'
                                   AND COLUMN_NAME = 'store_id'
                                """)
                        .query(String.class)
                        .single();
        Long maxLength =
                jdbcClient
                        .sql(
                                """
                                SELECT CHARACTER_MAXIMUM_LENGTH
                                  FROM information_schema.COLUMNS
                                 WHERE TABLE_SCHEMA = DATABASE()
                                   AND TABLE_NAME = 'store_profile'
                                   AND COLUMN_NAME = 'store_id'
                                """)
                        .query(Long.class)
                        .single();

        assertThat(dataType).isEqualToIgnoringCase("binary");
        assertThat(maxLength).isEqualTo(16L);
    }
}
