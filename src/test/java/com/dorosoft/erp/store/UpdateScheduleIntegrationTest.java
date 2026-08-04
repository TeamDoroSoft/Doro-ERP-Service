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
@Import({TestcontainersConfiguration.class, UpdateScheduleIntegrationTest.FailingAuditConfiguration.class})
class UpdateScheduleIntegrationTest {

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
        initialVersion = jdbcClient.sql("SELECT version FROM store_profile").query(Long.class).single();
    }

    @AfterEach
    void restoreAuditWriter() {
        auditWriter.fail = false;
    }

    @Test
    void updatesSplitBusinessHoursAndWritesAudit() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/schedule")
                        .headers(actorHeaders("store.settings.update"))
                        .header("If-Match", "\"" + initialVersion + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedule.businessHours.MONDAY[0].start").value("09:00"))
                .andExpect(jsonPath("$.data.schedule.businessHours.MONDAY[0].end").value("14:00"))
                .andExpect(jsonPath("$.data.schedule.businessHours.MONDAY[1].start").value("15:00"))
                .andExpect(jsonPath("$.data.schedule.businessHours.MONDAY[1].end").value("22:00"))
                .andExpect(jsonPath("$.data.version").value(initialVersion + 1));

        assertThat(count("business_hour")).isEqualTo(2L);
        assertThat(currentVersion()).isEqualTo(initialVersion + 1);
        assertThat(jdbcClient
                        .sql("SELECT COUNT(*) FROM audit_record WHERE domain='STORE' AND action='STORE_SCHEDULE_UPDATED'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        assertScheduleAudit();
    }

    @Test
    void updatesProfileWithCurrentVersionAfterScheduleRowsExist() throws Exception {
        update(validBody(), "store.settings.update", String.valueOf(initialVersion))
                .andExpect(status().isOk());

        long scheduleVersion = currentVersion();
        assertThat(count("business_hour")).isPositive();

        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders("store.settings.update"))
                        .header("If-Match", String.valueOf(scheduleVersion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"일정 저장 후 변경","address":"서울시 중구","contact":"02-1234-5678","timeZone":"Asia/Seoul"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.name").value("일정 저장 후 변경"));

        assertThat(count("business_hour")).isEqualTo(2L);
        assertThat(currentVersion()).isGreaterThan(scheduleVersion);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidSchedules(String body, String code, String field) throws Exception {
        update(body, "store.settings.update", String.valueOf(initialVersion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
        assertScheduleUnchanged();
    }

    static java.util.stream.Stream<Arguments> invalidBodies() {
        return java.util.stream.Stream.of(
                Arguments.of(body("[{\"start\":\"09:00\",\"end\":\"09:00\"}]", "[]", "[]", "{}"), "VALIDATION_FAILED", "businessHours.MONDAY[0]"),
                Arguments.of(body("[{\"start\":\"09:00\",\"end\":\"14:00\"},{\"start\":\"13:00\",\"end\":\"18:00\"}]", "[]", "[]", "{}"), "OVERLAPPING_BUSINESS_HOURS", "businessHours.MONDAY"),
                Arguments.of(body("[{\"start\":\"09:00\",\"end\":\"14:00\"}]", "[]", "[]", "{\"ORDER\":{\"MONDAY\":[{\"start\":\"08:00\",\"end\":\"10:00\"}]}}"), "SERVICE_WINDOW_OUTSIDE_BUSINESS_HOURS", "serviceWindows.ORDER.MONDAY"),
                Arguments.of(body("[{\"start\":\"09:00\",\"end\":\"14:00\"}]", "[\"MONDAY\"]", "[]", "{}"), "CLOSED_DAY_HAS_BUSINESS_HOURS", "businessHours.MONDAY"),
                Arguments.of(body("[]", "[]", "[{\"date\":\"2026-08-15\",\"reason\":\"점검\"},{\"date\":\"2026-08-15\",\"reason\":\"휴무\"}]", "{}"), "DUPLICATE_TEMPORARY_CLOSURE", "temporaryClosures"));
    }

    @Test
    void missingIfMatchReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/schedule")
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
        assertScheduleUnchanged();
    }

    @Test
    void staleVersionDoesNotChangeDatabase() throws Exception {
        update(validBody(), "store.settings.update", String.valueOf(initialVersion + 10))
                .andExpect(status().isConflict());
        assertScheduleUnchanged();
    }

    @Test
    void missingStoreReturns404() throws Exception {
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
        update(validBody(), "store.settings.update", "0").andExpect(status().isNotFound());
    }

    @Test
    void auditFailureRollsBackSchedule() throws Exception {
        auditWriter.fail = true;
        update(validBody(), "store.settings.update", String.valueOf(initialVersion))
                .andExpect(status().isInternalServerError());
        assertScheduleUnchanged();
        assertThat(count("audit_record")).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions update(
            String body, String permission, String version) throws Exception {
        return mockMvc.perform(put("/api/v1/store-settings/schedule")
                .headers(actorHeaders(permission))
                .header("If-Match", version)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assertScheduleUnchanged() {
        assertThat(count("business_hour")).isZero();
        assertThat(count("regular_closed_day")).isZero();
        assertThat(count("temporary_closure")).isZero();
        assertThat(count("service_window")).isZero();
        assertThat(currentVersion()).isEqualTo(initialVersion);
    }

    private void assertScheduleAudit() {
        assertThat(jdbcClient.sql("""
                        SELECT JSON_LENGTH(JSON_KEYS(after_value)) = 5
                           AND JSON_CONTAINS(
                               JSON_KEYS(after_value),
                               JSON_ARRAY('businessHours','regularClosedDays','temporaryClosures','serviceWindows','version'))
                           AND JSON_CONTAINS_PATH(after_value, 'one', '$.temporaryClosures[0].reason') = 0
                           AND JSON_UNQUOTE(JSON_EXTRACT(
                               after_value, '$.temporaryClosures[0].reasonCode')) = 'OTHER'
                           AND JSON_SEARCH(after_value, 'one', '%010%') IS NULL
                           AND value_schema_version = '2'
                        FROM audit_record WHERE action = 'STORE_SCHEDULE_UPDATED'
                        """).query(Integer.class).single())
                .isEqualTo(1);
        assertThat(jdbcClient.sql("""
                        SELECT COUNT(*) FROM audit_record_target t
                        JOIN audit_record r ON r.audit_id = t.audit_id
                        WHERE r.action = 'STORE_SCHEDULE_UPDATED'
                          AND ((t.relation_type = 'PRIMARY' AND t.target_type = 'STORE_SCHEDULE')
                            OR (t.relation_type = 'STORE' AND t.target_type = 'STORE_PROFILE'))
                        """).query(Long.class).single())
                .isEqualTo(2L);
        assertThat(jdbcClient.sql("""
                        SELECT COUNT(*) FROM audit_record_target t
                        JOIN audit_record r ON r.audit_id = t.audit_id
                        WHERE r.action = 'STORE_SCHEDULE_UPDATED'
                        """).query(Long.class).single())
                .isEqualTo(2L);
    }

    private long count(String table) {
        return StoreIntegrationSupport.countOf(jdbcClient, table);
    }

    private long currentVersion() {
        return jdbcClient.sql("SELECT version FROM store_profile").query(Long.class).single();
    }

    private static String validBody() {
        return body(
                "[{\"start\":\"09:00\",\"end\":\"14:00\"},{\"start\":\"15:00\",\"end\":\"22:00\"}]",
                "[\"SUNDAY\"]",
                "[{\"date\":\"2026-08-15\",\"reason\":\"시설 점검 010-1234-5678\"}]",
                "{\"ORDER\":{\"MONDAY\":[{\"start\":\"09:30\",\"end\":\"13:30\"}]},\"RESERVATION\":{\"MONDAY\":[{\"start\":\"15:30\",\"end\":\"21:30\"}]}}");
    }

    private static String body(
            String mondayHours, String closedDays, String closures, String serviceWindows) {
        return "{\"businessHours\":{\"MONDAY\":" + mondayHours
                + "},\"regularClosedDays\":" + closedDays
                + ",\"temporaryClosures\":" + closures
                + ",\"serviceWindows\":" + serviceWindows + "}";
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
