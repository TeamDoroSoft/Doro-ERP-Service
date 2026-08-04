package com.dorosoft.erp.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
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

@SpringBootTest(properties = "doro.store.bootstrap.enabled=false")
@Import(TestcontainersConfiguration.class)
@DisplayName("Store 스키마 제약 통합 테스트 - Migration이 선언한 UNIQUE/CHECK 제약이 실제 MySQL에서 강제되는지 네이티브 SQL로 검증한다")
class StoreSchemaConstraintIntegrationTest {

    @Autowired private JdbcClient jdbcClient;

    private UUID storeId;

    @BeforeEach
    void 테이블을_비우고_부모행을_만든다() {
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
        storeId = StoreIntegrationSupport.insertStoreProfile(jdbcClient, "제약 검증용 매장");
    }

    // --- UNIQUE 제약 ---------------------------------------------------------

    @Test
    @DisplayName("business_hour는 (store_id, day_of_week, period_order)가 중복되면 저장에 실패한다")
    void business_hour_슬롯_중복은_거부된다() {
        insertBusinessHour((short) 1, (short) 0, "09:00:00", "18:00:00");

        assertThatThrownBy(() -> insertBusinessHour((short) 1, (short) 0, "19:00:00", "22:00:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_business_hour_slot");
    }

    @Test
    @DisplayName("temporary_closure는 (store_id, closure_date)가 중복되면 저장에 실패한다")
    void temporary_closure_날짜_중복은_거부된다() {
        insertTemporaryClosure("2026-01-01");

        assertThatThrownBy(() -> insertTemporaryClosure("2026-01-01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_temporary_closure_date");
    }

    @Test
    @DisplayName("service_window는 (store_id, service_type, day_of_week, period_order)가 중복되면 저장에 실패한다")
    void service_window_슬롯_중복은_거부된다() {
        insertServiceWindow("ORDER", (short) 1, (short) 0, "09:00:00", "18:00:00");

        assertThatThrownBy(
                        () -> insertServiceWindow("ORDER", (short) 1, (short) 0, "10:00:00", "17:00:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_service_window_slot");
    }

    // --- CHECK 제약 ----------------------------------------------------------

    @Test
    @DisplayName("business_hour.day_of_week는 1~7 범위를 벗어나면 저장에 실패한다")
    void business_hour_요일_범위_위반은_거부된다() {
        assertThatThrownBy(() -> insertBusinessHour((short) 0, (short) 0, "09:00:00", "18:00:00"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_business_hour_day_of_week");

        assertThatThrownBy(() -> insertBusinessHour((short) 8, (short) 0, "09:00:00", "18:00:00"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_business_hour_day_of_week");
    }

    @Test
    @DisplayName("business_hour.period_order는 음수면 저장에 실패한다")
    void business_hour_구간순서_음수는_거부된다() {
        assertThatThrownBy(() -> insertBusinessHour((short) 1, (short) -1, "09:00:00", "18:00:00"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_business_hour_period_order");
    }

    @Test
    @DisplayName("business_hour는 시작 시각과 종료 시각이 같으면 저장에 실패한다")
    void business_hour_시작과_종료가_같으면_거부된다() {
        assertThatThrownBy(() -> insertBusinessHour((short) 1, (short) 0, "09:00:00", "09:00:00"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_business_hour_time_range");
    }

    @Test
    @DisplayName("service_window.service_type은 ORDER/RESERVATION 외의 값이면 저장에 실패한다")
    void service_window_서비스타입_위반은_거부된다() {
        assertThatThrownBy(
                        () -> insertServiceWindow("DELIVERY", (short) 1, (short) 0, "09:00:00", "18:00:00"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_service_window_service_type");
    }

    @Test
    @DisplayName("feature_setting.feature_code는 승인된 4종 외의 값이면 저장에 실패한다")
    void feature_setting_코드_위반은_거부된다() {
        assertThatThrownBy(() -> insertFeatureSetting("UNKNOWN", true))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_feature_setting_code");
    }

    @Test
    @DisplayName("notification_event_setting.event_code는 승인된 13종 외의 값이면 저장에 실패한다")
    void notification_event_setting_코드_위반은_거부된다() {
        assertThatThrownBy(() -> insertNotificationEventSetting("UNKNOWN", false))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_notification_event_setting_code");
    }

    // --- 허용되어야 하는 케이스 ------------------------------------------------

    @Test
    @DisplayName("business_hour는 자정을 넘기는 구간(22:00~02:00)을 허용한다")
    void business_hour_자정초과_구간은_허용된다() {
        assertThatCode(() -> insertBusinessHour((short) 5, (short) 0, "22:00:00", "02:00:00"))
                .doesNotThrowAnyException();

        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "business_hour")).isEqualTo(1L);
    }

    @Test
    @DisplayName("service_window도 자정을 넘기는 구간(22:00~02:00)을 허용한다")
    void service_window_자정초과_구간은_허용된다() {
        assertThatCode(
                        () -> insertServiceWindow("ORDER", (short) 5, (short) 0, "22:00:00", "02:00:00"))
                .doesNotThrowAnyException();

        assertThat(StoreIntegrationSupport.countOf(jdbcClient, "service_window")).isEqualTo(1L);
    }

    // --- 네이티브 INSERT 헬퍼 --------------------------------------------------

    private void insertBusinessHour(short dayOfWeek, short periodOrder, String start, String end) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO business_hour
                            (business_hour_id, store_id, day_of_week, period_order, start_local_time, end_local_time)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .param(StoreIntegrationSupport.toBinary(UUID.randomUUID()))
                .param(StoreIntegrationSupport.toBinary(storeId))
                .param(dayOfWeek)
                .param(periodOrder)
                .param(start)
                .param(end)
                .update();
    }

    private void insertTemporaryClosure(String closureDate) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO temporary_closure
                            (temporary_closure_id, store_id, closure_date, reason, created_at)
                        VALUES (?, ?, ?, ?, NOW(6))
                        """)
                .param(StoreIntegrationSupport.toBinary(UUID.randomUUID()))
                .param(StoreIntegrationSupport.toBinary(storeId))
                .param(closureDate)
                .param("내부 공사")
                .update();
    }

    private void insertServiceWindow(
            String serviceType, short dayOfWeek, short periodOrder, String start, String end) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO service_window
                            (service_window_id, store_id, service_type, day_of_week, period_order,
                             start_local_time, end_local_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)
                .param(StoreIntegrationSupport.toBinary(UUID.randomUUID()))
                .param(StoreIntegrationSupport.toBinary(storeId))
                .param(serviceType)
                .param(dayOfWeek)
                .param(periodOrder)
                .param(start)
                .param(end)
                .update();
    }

    private void insertFeatureSetting(String featureCode, boolean enabled) {
        jdbcClient
                .sql("INSERT INTO feature_setting (store_id, feature_code, enabled) VALUES (?, ?, ?)")
                .param(StoreIntegrationSupport.toBinary(storeId))
                .param(featureCode)
                .param(enabled)
                .update();
    }

    private void insertNotificationEventSetting(String eventCode, boolean enabled) {
        jdbcClient
                .sql(
                        "INSERT INTO notification_event_setting (store_id, event_code, enabled) VALUES (?, ?, ?)")
                .param(StoreIntegrationSupport.toBinary(storeId))
                .param(eventCode)
                .param(enabled)
                .update();
    }
}
