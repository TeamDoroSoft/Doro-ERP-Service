package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.bootstrap.CatalogBootstrapService;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Catalog Bootstrap 통합 테스트 - 배포 직후 catalog_revision 초기화의 멱등성을 실제 DB로 검증한다")
class CatalogBootstrapIntegrationTest {

    @Autowired private CatalogBootstrapService bootstrapService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
    }

    @Test
    @DisplayName("bootstrap을 두 번 호출해도 행 수와 catalogId, revision이 그대로다 (멱등성)")
    void 두_번_호출해도_상태가_같다() {
        CatalogRevision first = transactionTemplate.execute(status -> bootstrapService.bootstrap());
        CatalogRevision second = transactionTemplate.execute(status -> bootstrapService.bootstrap());

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.catalogId()).isEqualTo(first.catalogId());
        assertThat(second.revision()).isEqualTo(first.revision());

        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "catalog_revision")).isEqualTo(1L);
    }

    @Test
    @DisplayName("최초 생성된 catalog_revision의 revision은 0이다")
    void 최초_revision은_0이다() {
        CatalogRevision created = transactionTemplate.execute(status -> bootstrapService.bootstrap());

        assertThat(created).isNotNull();
        assertThat(created.revision()).isZero();
    }
}
