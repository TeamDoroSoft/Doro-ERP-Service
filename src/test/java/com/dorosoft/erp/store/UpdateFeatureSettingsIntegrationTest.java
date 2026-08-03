package com.dorosoft.erp.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditWriteResult;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.store.application.bootstrap.StoreBootstrapService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "doro.store.bootstrap.enabled=false")
@AutoConfigureMockMvc
@Import({
    TestcontainersConfiguration.class,
    UpdateFeatureSettingsIntegrationTest.FailingAuditConfiguration.class
})
class UpdateFeatureSettingsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbcClient;
    @Autowired StoreBootstrapService bootstrapService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired FailingAuditWriter auditWriter;

    private long initialVersion;

    @BeforeEach
    void cleanAndBootstrap() {
        jdbcClient.sql("DELETE FROM audit_record_target").update();
        jdbcClient.sql("DELETE FROM audit_record").update();
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
        transactionTemplate.executeWithoutResult(status -> bootstrapService.bootstrap());
        initialVersion = currentVersion();
    }

    @AfterEach
    void restoreAuditWriter() {
        auditWriter.fail = false;
    }

    @Test
    void updatesAllFeatureSettingsAndWritesAudit() throws Exception {
        update(validBody(), "store.settings.update", "\"" + initialVersion + "\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.features.customerFeatures.WAITING").value(false))
                .andExpect(jsonPath("$.data.features.customerFeatures.RESERVATION").value(false))
                .andExpect(jsonPath("$.data.features.customerFeatures.QR_ORDER").value(false))
                .andExpect(jsonPath("$.data.features.customerFeatures.PICKUP_ORDER").value(true))
                .andExpect(jsonPath("$.data.features.notificationEvents.WAITING_REGISTERED").value(true))
                .andExpect(jsonPath("$.data.features.notificationEvents.PAYMENT_CANCELLED").value(true))
                .andExpect(jsonPath("$.data.version").value(initialVersion + 1));

        assertThat(enabledCount("feature_setting")).isEqualTo(1L);
        assertThat(enabledCount("notification_event_setting")).isEqualTo(13L);
        assertThat(currentVersion()).isEqualTo(initialVersion + 1);
        assertThat(jdbcClient
                        .sql("SELECT COUNT(*) FROM audit_record WHERE domain='STORE' AND action='STORE_FEATURE_SETTINGS_UPDATED'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidSettingCodesAndMissingCodes(String body) throws Exception {
        update(body, "store.settings.update", String.valueOf(initialVersion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SETTING_CODE"));
        assertFeaturesUnchanged();
    }

    static java.util.stream.Stream<Arguments> invalidBodies() {
        return java.util.stream.Stream.of(
                Arguments.of(body(customerFeatures() + ",\"UNKNOWN_FEATURE\":true", notificationEvents())),
                Arguments.of(body(customerFeatures(), notificationEvents() + ",\"UNKNOWN_EVENT\":true")),
                Arguments.of(body(
                        "\"WAITING\":false,\"RESERVATION\":false,\"QR_ORDER\":false",
                        notificationEvents())),
                Arguments.of(body(customerFeatures(), notificationEventsWithoutPaymentCancelled())));
    }

    @Test
    void missingCustomerFeatureCodeReportsCustomerFeaturesField() throws Exception {
        update(
                        body("\"WAITING\":false,\"RESERVATION\":false,\"QR_ORDER\":false", notificationEvents()),
                        "store.settings.update",
                        String.valueOf(initialVersion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SETTING_CODE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("customerFeatures"));
    }

    @Test
    void missingNotificationEventCodeReportsNotificationEventsField() throws Exception {
        update(
                        body(customerFeatures(), notificationEventsWithoutPaymentCancelled()),
                        "store.settings.update",
                        String.valueOf(initialVersion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SETTING_CODE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("notificationEvents"));
    }

    @Test
    void missingIfMatchReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/features")
                        .headers(actorHeaders("store.settings.update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("If-Match"));
    }

    @Test
    void missingPermissionDoesNotChangeDatabase() throws Exception {
        update(validBody(), "store.settings.read", String.valueOf(initialVersion))
                .andExpect(status().isForbidden());
        assertFeaturesUnchanged();
    }

    @Test
    void staleVersionDoesNotChangeDatabase() throws Exception {
        update(validBody(), "store.settings.update", String.valueOf(initialVersion + 10))
                .andExpect(status().isConflict());
        assertFeaturesUnchanged();
    }

    @Test
    void missingStoreReturns404() throws Exception {
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
        update(validBody(), "store.settings.update", "0").andExpect(status().isNotFound());
    }

    @Test
    void auditFailureRollsBackFeatureSettings() throws Exception {
        auditWriter.fail = true;
        update(validBody(), "store.settings.update", String.valueOf(initialVersion))
                .andExpect(status().isInternalServerError());
        assertFeaturesUnchanged();
        assertThat(count("audit_record")).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions update(
            String body, String permission, String version) throws Exception {
        return mockMvc.perform(put("/api/v1/store-settings/features")
                .headers(actorHeaders(permission))
                .header("If-Match", version)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assertFeaturesUnchanged() {
        assertThat(count("feature_setting")).isEqualTo(4L);
        assertThat(enabledCount("feature_setting")).isEqualTo(3L);
        assertThat(count("notification_event_setting")).isEqualTo(13L);
        assertThat(enabledCount("notification_event_setting")).isZero();
        assertThat(currentVersion()).isEqualTo(initialVersion);
    }

    private long count(String table) {
        return StoreIntegrationSupport.countOf(jdbcClient, table);
    }

    private long enabledCount(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE enabled = true")
                .query(Long.class)
                .single();
    }

    private long currentVersion() {
        return jdbcClient.sql("SELECT version FROM store_profile").query(Long.class).single();
    }

    private static String validBody() {
        return body(customerFeatures(), notificationEvents());
    }

    private static String customerFeatures() {
        return "\"WAITING\":false,\"RESERVATION\":false,\"QR_ORDER\":false,\"PICKUP_ORDER\":true";
    }

    private static String notificationEvents() {
        return notificationEventsWithoutPaymentCancelled() + ",\"PAYMENT_CANCELLED\":true";
    }

    private static String notificationEventsWithoutPaymentCancelled() {
        return "\"WAITING_REGISTERED\":true,\"WAITING_CALLED\":true,"
                + "\"RESERVATION_REQUESTED\":true,\"RESERVATION_APPROVED\":true,"
                + "\"RESERVATION_REJECTED\":true,\"RESERVATION_CHANGED\":true,"
                + "\"RESERVATION_CHANGE_REJECTED\":true,\"RESERVATION_CANCELLED\":true,"
                + "\"RESERVATION_REMINDER\":true,\"PICKUP_ORDER_RECEIVED\":true,"
                + "\"PICKUP_READY\":true,\"PAYMENT_COMPLETED\":true";
    }

    private static String body(String customerFeatures, String notificationEvents) {
        return "{\"customerFeatures\":{" + customerFeatures
                + "},\"notificationEvents\":{" + notificationEvents + "}}";
    }

    private static HttpHeaders actorHeaders(String permissions) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Doro-Actor-Id", "actor-1");
        headers.set("X-Doro-Actor-Role", "STORE_ADMIN");
        headers.set("X-Doro-Actor-Permissions", permissions);
        return headers;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration {
        @Bean
        @Primary
        FailingAuditWriter failingAuditWriter(@Qualifier("jpaAuditWriter") AuditWriter delegate) {
            return new FailingAuditWriter(delegate);
        }
    }

    static final class FailingAuditWriter implements AuditWriter {
        private final AuditWriter delegate;
        private boolean fail;

        FailingAuditWriter(AuditWriter delegate) {
            this.delegate = delegate;
        }

        @Override
        public AuditWriteResult record(AuditRecordCommand command, AuditContext context) {
            if (fail) {
                throw new RuntimeException("forced audit failure");
            }
            return delegate.record(command, context);
        }
    }
}
