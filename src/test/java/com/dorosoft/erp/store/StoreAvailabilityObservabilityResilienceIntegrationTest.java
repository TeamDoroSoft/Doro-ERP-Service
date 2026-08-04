package com.dorosoft.erp.store;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.shared.config.FixedClockTestConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "doro.store.bootstrap.enabled=false")
@AutoConfigureMockMvc
@Import({
    TestcontainersConfiguration.class,
    FixedClockTestConfiguration.class,
    StoreAvailabilityObservabilityResilienceIntegrationTest.ThrowingRegistryConfiguration.class
})
class StoreAvailabilityObservabilityResilienceIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbcClient;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM audit_record_target").update();
        jdbcClient.sql("DELETE FROM audit_record").update();
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
    }

    @Test
    void metricFailureDoesNotReplaceStoreNotInitializedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/store/availability").param("featureCode", "WAITING"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ThrowingRegistryConfiguration {

        @Bean
        @Primary
        MeterRegistry throwingMeterRegistry() {
            return new ThrowingMeterRegistry();
        }
    }

    static class ThrowingMeterRegistry extends SimpleMeterRegistry {

        @Override
        protected Counter newCounter(Meter.Id id) {
            if (id.getName().startsWith("store.availability.")) {
                throw new IllegalStateException("counter registration failed");
            }
            return super.newCounter(id);
        }

        @Override
        protected Timer newTimer(
                Meter.Id id,
                DistributionStatisticConfig distributionStatisticConfig,
                PauseDetector pauseDetector) {
            if (id.getName().startsWith("store.availability.")) {
                throw new IllegalStateException("timer registration failed");
            }
            return super.newTimer(id, distributionStatisticConfig, pauseDetector);
        }
    }
}
