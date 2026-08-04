package com.dorosoft.erp.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.store.application.bootstrap.StoreBootstrapService;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "doro.store.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Store Bootstrap 통합 테스트 - 배포 직후 초기 매장 설정 생성의 멱등성과 승인된 기본값을 실제 DB로 검증한다")
class StoreBootstrapIntegrationTest {

    private static final Map<String, Boolean> EXPECTED_FEATURES =
            Map.of(
                    "WAITING", true,
                    "RESERVATION", true,
                    "QR_ORDER", true,
                    "PICKUP_ORDER", false);

    private static final String[] EXPECTED_EVENT_CODES = {
        "WAITING_REGISTERED",
        "WAITING_CALLED",
        "RESERVATION_REQUESTED",
        "RESERVATION_APPROVED",
        "RESERVATION_REJECTED",
        "RESERVATION_CHANGED",
        "RESERVATION_CHANGE_REJECTED",
        "RESERVATION_CANCELLED",
        "RESERVATION_REMINDER",
        "PICKUP_ORDER_RECEIVED",
        "PICKUP_READY",
        "PAYMENT_COMPLETED",
        "PAYMENT_CANCELLED"
    };

    @Autowired private StoreBootstrapService bootstrapService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
    }

    @Test
    @DisplayName("bootstrap을 두 번 호출해도 행 수와 storeId가 그대로다 (멱등성)")
    void 두_번_호출해도_상태가_같다() {
        StoreSettings first = transactionTemplate.execute(status -> bootstrapService.bootstrap());
        StoreSettings second = transactionTemplate.execute(status -> bootstrapService.bootstrap());

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(second.storeId()).isEqualTo(first.storeId());

        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "store_profile")).isEqualTo(1L);
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "feature_setting")).isEqualTo(4L);
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "notification_event_setting"))
                .isEqualTo(13L);
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "business_hour")).isZero();
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "service_window")).isZero();
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "regular_closed_day")).isZero();
        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "temporary_closure")).isZero();
    }

    @Test
    @DisplayName("고객 기능 기본값은 WAITING/RESERVATION/QR_ORDER만 활성이고 PICKUP_ORDER는 비활성이다")
    void 고객기능_기본값이_승인된_값과_같다() {
        StoreSettings created = transactionTemplate.execute(status -> bootstrapService.bootstrap());

        Map<String, Boolean> stored = readToggles("feature_setting", "feature_code");

        assertThat(stored).containsExactlyInAnyOrderEntriesOf(EXPECTED_FEATURES);

        assertThat(created).isNotNull();
        assertThat(created.features().isEnabled(FeatureCode.WAITING)).isTrue();
        assertThat(created.features().isEnabled(FeatureCode.RESERVATION)).isTrue();
        assertThat(created.features().isEnabled(FeatureCode.QR_ORDER)).isTrue();
        assertThat(created.features().isEnabled(FeatureCode.PICKUP_ORDER)).isFalse();
    }

    @Test
    @DisplayName("알림 이벤트 13종이 정확히 저장되고 모두 비활성이다")
    void 알림이벤트_기본값은_13종_전부_비활성이다() {
        StoreSettings created = transactionTemplate.execute(status -> bootstrapService.bootstrap());

        Map<String, Boolean> stored = readToggles("notification_event_setting", "event_code");

        assertThat(stored).hasSize(13);
        assertThat(stored.keySet()).containsExactlyInAnyOrder(EXPECTED_EVENT_CODES);
        assertThat(stored.values()).containsOnly(Boolean.FALSE);

        assertThat(created).isNotNull();
        for (NotificationEventCode code : NotificationEventCode.values()) {
            assertThat(created.features().isNotificationEnabled(code)).isFalse();
        }
    }

    private Map<String, Boolean> readToggles(String table, String codeColumn) {
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        jdbcClient
                .sql("SELECT " + codeColumn + ", enabled FROM " + table)
                .query(
                        (rs, rowNum) -> {
                            toggles.put(rs.getString(1), rs.getBoolean(2));
                            return null;
                        })
                .list();
        return toggles;
    }
}
