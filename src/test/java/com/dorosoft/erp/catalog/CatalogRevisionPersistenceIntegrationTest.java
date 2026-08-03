package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.bootstrap.CatalogBootstrapService;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Catalog Revision 영속화 통합 테스트 - 낙관적 잠금과 업체 Schema 단일 행 전제를 포트(CatalogRevisionRepository) 수준에서 검증한다")
class CatalogRevisionPersistenceIntegrationTest {

    @Autowired private CatalogRevisionRepository repository;
    @Autowired private CatalogBootstrapService bootstrapService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
    }

    // --- 낙관적 잠금 ----------------------------------------------------------

    @Test
    @DisplayName("먼저 저장한 쪽만 성공하고 revision이 오른 뒤, 낡은 revision으로 저장하면 OptimisticLockingFailureException이 난다")
    void 낡은_revision으로_저장하면_실패한다() {
        CatalogRevision created = bootstrap();

        CatalogRevision r1 = findCurrentInTransaction().orElseThrow();
        CatalogRevision r2 = findCurrentInTransaction().orElseThrow();
        assertThat(r1.revision()).isEqualTo(created.revision());
        assertThat(r2.revision()).isEqualTo(created.revision());

        CatalogRevision saved = transactionTemplate.execute(status -> repository.save(r1));

        assertThat(saved).isNotNull();
        assertThat(saved.revision()).isGreaterThan(r1.revision());

        assertThatThrownBy(() -> transactionTemplate.execute(status -> repository.save(r2)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("일치하는 revision으로 저장하면 값 변경이 없어도 revision이 1 증가한다")
    void 값_변경_없이_저장해도_revision이_증가한다() {
        CatalogRevision created = bootstrap();

        CatalogRevision saved = transactionTemplate.execute(status -> repository.save(created));

        assertThat(saved).isNotNull();
        assertThat(saved.catalogId()).isEqualTo(created.catalogId());
        assertThat(saved.revision()).isEqualTo(created.revision() + 1);
    }

    // --- 업체 Schema 격리 / 단일 행 전제 -----------------------------------------

    @Test
    @DisplayName("findCurrent는 파라미터 없이 업체 Schema의 유일한 Catalog Revision을 돌려준다")
    void 파라미터_없이_유일_행을_조회한다() {
        CatalogRevision created = bootstrap();

        Optional<CatalogRevision> found = findCurrentInTransaction();

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().catalogId()).isEqualTo(created.catalogId());
        assertThat(existsInTransaction()).isTrue();
    }

    @Test
    @DisplayName("행이 하나도 없으면 findCurrent는 비어 있고 exists는 false다")
    void 행이_없으면_비어있다() {
        assertThat(findCurrentInTransaction()).isEmpty();
        assertThat(existsInTransaction()).isFalse();
    }

    @Test
    @DisplayName("catalog_revision이 2행이면 findCurrent가 행 수를 알리는 IllegalStateException을 던진다")
    void 행이_둘_이상이면_예외를_던진다() {
        bootstrap();
        UUID intruder = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
        assertThat(intruder).isNotNull();

        assertThatThrownBy(this::findCurrentInTransaction)
                .hasMessageContaining("실제 행 수: 2")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("catalog_revision이 하나만 존재해야 합니다")
                .hasMessageContaining("실제 행 수: 2");
    }

    // --- 헬퍼 ----------------------------------------------------------------

    private Optional<CatalogRevision> findCurrentInTransaction() {
        return transactionTemplate.execute(status -> repository.findCurrent());
    }

    private boolean existsInTransaction() {
        Boolean exists = transactionTemplate.execute(status -> repository.exists());
        return Boolean.TRUE.equals(exists);
    }

    private CatalogRevision bootstrap() {
        return transactionTemplate.execute(status -> bootstrapService.bootstrap());
    }
}
