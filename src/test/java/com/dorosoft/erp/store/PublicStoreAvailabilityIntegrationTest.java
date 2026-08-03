package com.dorosoft.erp.store;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.shared.config.FixedClockTestConfiguration;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.feature.FeatureCode;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
import com.dorosoft.erp.store.domain.feature.NotificationEventCode;
import com.dorosoft.erp.store.domain.schedule.BusinessPeriod;
import com.dorosoft.erp.store.domain.schedule.OperatingSchedule;
import com.dorosoft.erp.store.domain.schedule.ServiceType;
import com.dorosoft.erp.store.domain.schedule.ServiceWindow;
import com.dorosoft.erp.store.domain.schedule.TemporaryClosure;
import com.dorosoft.erp.store.domain.settings.StoreProfile;
import com.dorosoft.erp.store.domain.settings.StoreSettings;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
class PublicStoreAvailabilityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbcClient;
    @Autowired StoreSettingsRepository repository;
    @Autowired TransactionTemplate transactionTemplate;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM audit_record_target").update();
        jdbcClient.sql("DELETE FROM audit_record").update();
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
    }

    @Test
    void returnsOnlySixPublicFieldsWithoutAuthentication() throws Exception {
        saveStore(schedule(Set.of()), featuresWith(FeatureCode.WAITING));

        mockMvc.perform(get("/api/v1/store/availability").param("featureCode", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.*", hasSize(6)))
                .andExpect(jsonPath("$.data.storeName").value("도루 카페"))
                .andExpect(jsonPath("$.data.featureCode").value("WAITING"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.reason").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.evaluatedAt").value("2026-08-15T01:00:00Z"))
                .andExpect(jsonPath("$.data.nextAvailableAt").isEmpty())
                .andExpect(jsonPath("$.data.address").doesNotExist())
                .andExpect(jsonPath("$.data.contact").doesNotExist())
                .andExpect(jsonPath("$.data.timeZone").doesNotExist())
                .andExpect(jsonPath("$.data.version").doesNotExist())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void disabledFeatureIsNormalUnavailableResultWithoutNextAvailability() throws Exception {
        saveStore(schedule(Set.of()), FeatureSettings.allDisabled());

        mockMvc.perform(get("/api/v1/store/availability").param("featureCode", "QR_ORDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("FEATURE_DISABLED"))
                .andExpect(jsonPath("$.data.nextAvailableAt").isEmpty());
    }

    @Test
    void temporaryClosureIsNormalUnavailableResult() throws Exception {
        saveStore(
                schedule(Set.of(new TemporaryClosure(LocalDate.of(2026, 8, 15), "시설 점검"))),
                featuresWith(FeatureCode.QR_ORDER));

        mockMvc.perform(get("/api/v1/store/availability").param("featureCode", "QR_ORDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("TEMPORARILY_CLOSED"))
                .andExpect(jsonPath("$.data.nextAvailableAt").value("2026-08-16T00:00:00Z"));
    }

    @Test
    void missingStoreReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/store/availability").param("featureCode", "WAITING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_INITIALIZED"));
    }

    @Test
    void missingFeatureCodeReturnsValidationFailure() throws Exception {
        mockMvc.perform(get("/api/v1/store/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("featureCode"));
    }

    @Test
    void invalidFeatureCodeReturnsValidationFailure() throws Exception {
        mockMvc.perform(get("/api/v1/store/availability").param("featureCode", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("featureCode"));
    }

    private OperatingSchedule schedule(Set<TemporaryClosure> temporaryClosures) {
        Map<DayOfWeek, List<BusinessPeriod>> businessHours = Map.of(
                DayOfWeek.SATURDAY,
                List.of(new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(18, 0))),
                DayOfWeek.SUNDAY,
                List.of(new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(18, 0))));
        Set<ServiceWindow> serviceWindows = Set.of(
                new ServiceWindow(
                        ServiceType.ORDER,
                        DayOfWeek.SATURDAY,
                        0,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)),
                new ServiceWindow(
                        ServiceType.ORDER,
                        DayOfWeek.SUNDAY,
                        0,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)));
        return OperatingSchedule.of(businessHours, Set.of(), temporaryClosures, serviceWindows);
    }

    private static FeatureSettings featuresWith(FeatureCode enabledCode) {
        Map<FeatureCode, Boolean> features = new EnumMap<>(FeatureCode.class);
        for (FeatureCode code : FeatureCode.values()) {
            features.put(code, code == enabledCode);
        }
        Map<NotificationEventCode, Boolean> events = new EnumMap<>(NotificationEventCode.class);
        for (NotificationEventCode code : NotificationEventCode.values()) {
            events.put(code, false);
        }
        return FeatureSettings.of(features, events);
    }

    private void saveStore(OperatingSchedule schedule, FeatureSettings features) {
        StoreSettings settings = StoreSettings.create(
                UUID.randomUUID(),
                new StoreProfile(
                        "도루 카페", "서울특별시 중구 예시로 1", "02-1234-5678", ZoneId.of("Asia/Seoul")),
                schedule,
                features);
        transactionTemplate.executeWithoutResult(status -> repository.save(settings));
    }
}
