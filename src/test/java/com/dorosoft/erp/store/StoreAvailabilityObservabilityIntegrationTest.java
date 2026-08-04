package com.dorosoft.erp.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.shared.config.FixedClockTestConfiguration;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "doro.store.bootstrap.enabled=false")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockTestConfiguration.class})
class StoreAvailabilityObservabilityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbcClient;
    @Autowired StoreSettingsRepository repository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM audit_record_target").update();
        jdbcClient.sql("DELETE FROM audit_record").update();
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
    }

    @Test
    void recordsEvaluationForRequestOutsideBusinessHours() throws Exception {
        saveStore();
        double evaluationBefore = count(
                "store.availability.evaluations",
                "feature",
                "WAITING",
                "reason",
                "OUTSIDE_BUSINESS_HOURS",
                "caller",
                "public_api");
        double failureBefore = count(
                "store.availability.failures", "feature", "WAITING", "caller", "public_api");

        mockMvc.perform(get("/api/v1/store/availability").param("featureCode", "WAITING"))
                .andExpect(status().isOk());

        assertThat(count(
                                "store.availability.evaluations",
                                "feature",
                                "WAITING",
                                "reason",
                                "OUTSIDE_BUSINESS_HOURS",
                                "caller",
                                "public_api")
                        - evaluationBefore)
                .isEqualTo(1.0);
        assertThat(count(
                                "store.availability.failures",
                                "feature",
                                "WAITING",
                                "caller",
                                "public_api")
                        - failureBefore)
                .isZero();
    }

    @Test
    void recordsFailureWhenStoreIsNotInitialized() throws Exception {
        double failureBefore = count(
                "store.availability.failures",
                "feature",
                "WAITING",
                "caller",
                "public_api",
                "cause",
                "store_not_initialized");
        double evaluationBefore = count(
                "store.availability.evaluations", "feature", "WAITING", "caller", "public_api");

        mockMvc.perform(get("/api/v1/store/availability").param("featureCode", "WAITING"))
                .andExpect(status().isNotFound());

        assertThat(count(
                                "store.availability.failures",
                                "feature",
                                "WAITING",
                                "caller",
                                "public_api",
                                "cause",
                                "store_not_initialized")
                        - failureBefore)
                .isEqualTo(1.0);
        assertThat(count(
                                "store.availability.evaluations",
                                "feature",
                                "WAITING",
                                "caller",
                                "public_api")
                        - evaluationBefore)
                .isZero();
    }

    private double count(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private void saveStore() {
        Map<DayOfWeek, List<BusinessPeriod>> businessHours = Map.of(
                DayOfWeek.SATURDAY,
                List.of(new BusinessPeriod(0, LocalTime.of(11, 0), LocalTime.of(18, 0))));
        OperatingSchedule schedule =
                OperatingSchedule.of(businessHours, Set.of(), Set.of(), Set.of());
        Map<FeatureCode, Boolean> features = new EnumMap<>(FeatureCode.class);
        for (FeatureCode code : FeatureCode.values()) {
            features.put(code, code == FeatureCode.WAITING);
        }
        Map<NotificationEventCode, Boolean> events = new EnumMap<>(NotificationEventCode.class);
        for (NotificationEventCode code : NotificationEventCode.values()) {
            events.put(code, false);
        }
        StoreSettings settings = StoreSettings.create(
                UUID.randomUUID(),
                new StoreProfile(
                        "도루 카페", "서울특별시 중구 예시로 1", "02-1234-5678", ZoneId.of("Asia/Seoul")),
                schedule,
                FeatureSettings.of(features, events));
        transactionTemplate.executeWithoutResult(status -> repository.save(settings));
    }
}
