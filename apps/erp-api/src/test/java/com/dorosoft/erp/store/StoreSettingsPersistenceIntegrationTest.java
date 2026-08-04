package com.dorosoft.erp.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import com.dorosoft.erp.store.application.bootstrap.StoreBootstrapService;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.ZoneId;
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

@SpringBootTest(properties = "doro.store.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Store 설정 영속화 통합 테스트 - 낙관적 잠금과 업체 Schema 단일 행 전제를 포트(StoreSettingsRepository) 수준에서 검증한다")
class StoreSettingsPersistenceIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired private StoreSettingsRepository repository;
    @Autowired private StoreBootstrapService bootstrapService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
    }

    // --- 낙관적 잠금 ----------------------------------------------------------

    @Test
    @DisplayName("먼저 저장한 쪽만 성공하고 version이 오른 뒤, 낡은 version으로 저장하면 OptimisticLockingFailureException이 난다")
    void 낡은_version으로_저장하면_실패한다() {
        bootstrap();

        StoreSettings s1 = findCurrentInTransaction().orElseThrow();
        StoreSettings s2 = findCurrentInTransaction().orElseThrow();
        long baseVersion = s1.version();
        assertThat(s2.version()).isEqualTo(baseVersion);

        s1.updateProfile(new StoreProfile("도로소프트 본점", "서울특별시 강남구 테헤란로 10", "02-1111-2222", SEOUL));
        StoreSettings saved = transactionTemplate.execute(status -> repository.save(s1));

        assertThat(saved).isNotNull();
        assertThat(saved.version()).isGreaterThan(baseVersion);

        s2.updateProfile(new StoreProfile("뒤늦은 수정", "서울특별시 서초구 1", "02-3333-4444", SEOUL));
        assertThatThrownBy(() -> transactionTemplate.execute(status -> repository.save(s2)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("저장한 프로필 값은 다시 읽어도 그대로다 (round-trip)")
    void 저장한_프로필이_그대로_조회된다() {
        bootstrap();

        StoreSettings settings = findCurrentInTransaction().orElseThrow();
        StoreProfile newProfile =
                new StoreProfile("도로소프트 강남점", "서울특별시 강남구 테헤란로 427", "02-5555-6666", SEOUL);
        settings.updateProfile(newProfile);
        transactionTemplate.execute(status -> repository.save(settings));

        StoreSettings reloaded = findCurrentInTransaction().orElseThrow();

        assertThat(reloaded.storeId()).isEqualTo(settings.storeId());
        assertThat(reloaded.profile()).isEqualTo(newProfile);
    }

    // --- 업체 Schema 격리 / 단일 행 전제 -----------------------------------------

    @Test
    @DisplayName("findCurrent는 파라미터 없이 업체 Schema의 유일한 매장 설정을 돌려준다")
    void 파라미터_없이_유일_행을_조회한다() {
        StoreSettings created = bootstrap();

        Optional<StoreSettings> found = findCurrentInTransaction();

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().storeId()).isEqualTo(created.storeId());
        assertThat(existsInTransaction()).isTrue();
    }

    @Test
    @DisplayName("행이 하나도 없으면 findCurrent는 비어 있고 exists는 false다")
    void 행이_없으면_비어있다() {
        assertThat(findCurrentInTransaction()).isEmpty();
        assertThat(existsInTransaction()).isFalse();
    }

    @Test
    @DisplayName("store_profile이 2행이면 findCurrent가 행 수를 알리는 IllegalStateException을 던진다 (@Repository 예외 변환으로 감싸져 전달된다)")
    void 행이_둘_이상이면_예외를_던진다() {
        bootstrap();
        UUID intruder = StoreIntegrationSupport.insertStoreProfile(jdbcClient, "있어서는 안 되는 두 번째 매장");
        assertThat(intruder).isNotNull();

        assertThatThrownBy(this::findCurrentInTransaction)
                .hasMessageContaining("실제 행 수: 2")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("store_profile이 하나만 존재해야 합니다")
                .hasMessageContaining("실제 행 수: 2");
    }

    // --- 헬퍼 ----------------------------------------------------------------

    /** 자식 컬렉션이 LAZY이고 open-in-view가 꺼져 있어 조회도 트랜잭션 안에서 해야 한다. */
    private Optional<StoreSettings> findCurrentInTransaction() {
        return transactionTemplate.execute(status -> repository.findCurrent());
    }

    private boolean existsInTransaction() {
        Boolean exists = transactionTemplate.execute(status -> repository.exists());
        return Boolean.TRUE.equals(exists);
    }

    private StoreSettings bootstrap() {
        return transactionTemplate.execute(status -> bootstrapService.bootstrap());
    }
}
