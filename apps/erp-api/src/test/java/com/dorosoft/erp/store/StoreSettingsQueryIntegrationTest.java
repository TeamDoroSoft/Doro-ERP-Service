package com.dorosoft.erp.store;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import com.dorosoft.erp.store.application.port.StoreSettingsRepository;
import com.dorosoft.erp.store.domain.feature.FeatureSettings;
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
@Import(TestcontainersConfiguration.class)
class StoreSettingsQueryIntegrationTest {

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
    void returnsCompleteSettings() throws Exception {
        OperatingSchedule schedule = OperatingSchedule.of(
                Map.of(DayOfWeek.MONDAY,
                        java.util.List.of(new BusinessPeriod(0, LocalTime.of(9, 0), LocalTime.of(22, 0)))),
                Set.of(DayOfWeek.SUNDAY),
                Set.of(new TemporaryClosure(LocalDate.of(2026, 8, 15), "시설 점검")),
                Set.of(new ServiceWindow(
                        ServiceType.ORDER, DayOfWeek.MONDAY, 0, LocalTime.of(9, 30), LocalTime.of(21, 30))));
        StoreSettings settings = StoreSettings.create(
                UUID.randomUUID(),
                new StoreProfile("도루 카페", "서울특별시 중구 예시로 1", "02-1234-5678", ZoneId.of("Asia/Seoul")),
                schedule,
                FeatureSettings.allDisabled());
        StoreSettings saved = transactionTemplate.execute(status -> repository.save(settings));

        mockMvc.perform(get("/api/v1/store-settings")
                        .with(StoreIntegrationSupport.actor("store.settings.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.name").value("도루 카페"))
                .andExpect(jsonPath("$.data.profile.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.schedule.businessHours.MONDAY[0].start").value("09:00"))
                .andExpect(jsonPath("$.data.schedule.regularClosedDays[0]").value("SUNDAY"))
                .andExpect(jsonPath("$.data.schedule.temporaryClosures[0].date").value("2026-08-15"))
                .andExpect(jsonPath("$.data.schedule.serviceWindows.ORDER.MONDAY[0].end").value("21:30"))
                .andExpect(jsonPath("$.data.features.customerFeatures.WAITING").value(false))
                .andExpect(jsonPath("$.data.features.notificationEvents.PAYMENT_CANCELLED").value(false))
                .andExpect(jsonPath("$.data.version").value(saved.version()))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void missingStoreReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/store-settings")
                        .with(StoreIntegrationSupport.actor("store.settings.read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_INITIALIZED"));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/store-settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void missingPermissionReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/store-settings")
                        .with(StoreIntegrationSupport.actor("store.settings.update")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

}
