package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.catalog.application.category.CreateCategoryService;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriteResult;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import com.dorosoft.erp.testsupport.MySqlTestcontainersConfiguration;
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

/**
 * 업무 변경과 Audit Append의 원자적 Commit/Rollback(MENU-10 인수 검증, 테스트 명세.md § 감사 계약 테스트).
 * {@code CreateCategoryService.createAndRecord}는 실제 삽입과 감사 기록을 같은(REQUIRES_NEW) Transaction
 * 안에서 실행한다 - Audit Append가 실패하면 방금 저장한 Category도 함께 Rollback되어야 한다.
 */
@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import({
    MySqlTestcontainersConfiguration.class,
    CatalogAuditAtomicityIntegrationTest.ThrowingAuditWriterConfig.class
})
@DisplayName("Catalog 감사 원자성 통합 테스트 - Audit Append 실패가 업무 변경을 함께 Rollback하는지 실제 MySQL로 검증한다")
class CatalogAuditAtomicityIntegrationTest {

    @Autowired private CreateCategoryService createCategoryService;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비우고_Catalog를_초기화한다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
    }

    @Test
    @DisplayName("Audit Append가 실패하면 같은 Transaction의 Category 생성도 함께 Rollback된다")
    void auditAppendFailureRollsBackCategoryCreation() {
        AuditContext auditContext = CatalogIntegrationSupport.testAuditContext();

        assertThatThrownBy(() -> createCategoryService.create("커피", null, auditContext))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Audit Append 실패(테스트 주입)");

        assertThat(CatalogIntegrationSupport.countOf(jdbcClient, "category")).isZero();
    }

    @TestConfiguration
    static class ThrowingAuditWriterConfig {
        @Bean
        @Primary
        AuditWriter throwingAuditWriter() {
            return new AuditWriter() {
                @Override
                public AuditWriteResult record(AuditRecordCommand command, AuditContext context) {
                    throw new RuntimeException("Audit Append 실패(테스트 주입)");
                }
            };
        }
    }
}
