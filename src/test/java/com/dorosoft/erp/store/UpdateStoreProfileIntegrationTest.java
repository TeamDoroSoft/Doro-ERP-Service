package com.dorosoft.erp.store;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.TestcontainersConfiguration;
import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditWriteResult;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.store.application.bootstrap.StoreBootstrapService;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
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
@Import({TestcontainersConfiguration.class, UpdateStoreProfileIntegrationTest.FailingAuditConfiguration.class})
class UpdateStoreProfileIntegrationTest {

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
    void updatesProfileAndWritesAudit() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders("store.settings.update"))
                        .header("If-Match", "\"" + initialVersion + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"기준 매장","address":"서울시 중구","contact":"02-1234-5678","timeZone":"Asia/Seoul"}
                                """))
                .andExpect(status().isOk());

        jdbcClient.sql("DELETE FROM audit_record_target").update();
        jdbcClient.sql("DELETE FROM audit_record").update();

        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders("store.settings.update"))
                        .header("If-Match", "\"" + (initialVersion + 1) + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  새 매장  ","address":"서울시 중구","contact":"02-1234-5678","timeZone":"Asia/Seoul"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.name").value("새 매장"))
                .andExpect(jsonPath("$.data.version").value(initialVersion + 2));

        String name = jdbcClient.sql("SELECT name FROM store_profile").query(String.class).single();
        Long version = jdbcClient.sql("SELECT version FROM store_profile").query(Long.class).single();
        org.assertj.core.api.Assertions.assertThat(name).isEqualTo("새 매장");
        org.assertj.core.api.Assertions.assertThat(version).isEqualTo(initialVersion + 2);
        org.assertj.core.api.Assertions.assertThat(jdbcClient
                        .sql("SELECT COUNT(*) FROM audit_record WHERE domain='STORE' AND action='STORE_PROFILE_UPDATED'")
                        .query(Long.class).single())
                .isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(jdbcClient
                        .sql("SELECT COUNT(*) FROM audit_record_target WHERE relation_type='PRIMARY'")
                        .query(Long.class).single())
                .isEqualTo(1L);
        assertProfileAudit("[\"name\"]");
    }

    @Test
    void writesOnlyAddressAndContactAsChangedFieldNames() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders("store.settings.update"))
                        .header("If-Match", String.valueOf(initialVersion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"미설정","address":"새 주소","contact":"031-111-2222","timeZone":"Asia/Seoul"}
                                """))
                .andExpect(status().isOk());

        assertProfileAudit("[\"address\", \"contact\"]");
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidFields(String body, String field) throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders("store.settings.update"))
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')]").exists());
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of(validBody("   ", "Asia/Seoul"), "name"),
                Arguments.of(validBody("x".repeat(101), "Asia/Seoul"), "name"),
                Arguments.of(validBody("정상", "Not/AZone"), "timeZone"),
                Arguments.of("{\"name\":\"정상\",\"address\":\"주소\",\"contact\":\"abc\",\"timeZone\":\"Asia/Seoul\"}", "contact"));
    }

    @Test
    void missingIfMatchReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders("store.settings.update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("정상", "Asia/Seoul")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("If-Match"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REQUIRED"));
    }

    @Test
    void missingPermissionDoesNotChangeDatabase() throws Exception {
        updateExpecting(
                "store.settings.read",
                String.valueOf(initialVersion),
                validBody("변경 금지", "Asia/Seoul"),
                403);
        assertUnchanged();
    }

    @Test
    void staleVersionDoesNotChangeDatabase() throws Exception {
        updateExpecting(
                "store.settings.update",
                String.valueOf(initialVersion + 100),
                validBody("변경 금지", "Asia/Seoul"),
                409);
        assertUnchanged();
    }

    @Test
    void missingStoreReturns404() throws Exception {
        StoreIntegrationSupport.cleanStoreTables(jdbcClient);
        updateExpecting("store.settings.update", "0", validBody("새 매장", "Asia/Seoul"), 404);
    }

    @Test
    void auditFailureRollsBackProfile() throws Exception {
        auditWriter.fail = true;

        updateExpecting(
                "store.settings.update",
                String.valueOf(initialVersion),
                validBody("롤백 대상", "Asia/Seoul"),
                500);

        assertUnchanged();
        org.assertj.core.api.Assertions.assertThat(
                        jdbcClient.sql("SELECT COUNT(*) FROM audit_record").query(Long.class).single())
                .isZero();
    }

    private void updateExpecting(String permission, String version, String body, int expectedStatus)
            throws Exception {
        mockMvc.perform(put("/api/v1/store-settings/profile")
                        .headers(actorHeaders(permission))
                        .header("If-Match", version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    private void assertUnchanged() {
        org.assertj.core.api.Assertions.assertThat(
                        jdbcClient.sql("SELECT name FROM store_profile").query(String.class).single())
                .isEqualTo("미설정");
        org.assertj.core.api.Assertions.assertThat(
                        jdbcClient.sql("SELECT version FROM store_profile").query(Long.class).single())
                .isEqualTo(initialVersion);
    }

    private void assertProfileAudit(String expectedChangedFields) {
        org.assertj.core.api.Assertions.assertThat(jdbcClient.sql("""
                        SELECT JSON_LENGTH(JSON_KEYS(after_value)) = 4
                           AND JSON_CONTAINS(
                               JSON_KEYS(after_value),
                               JSON_ARRAY('storeName','timeZone','changedFields','version'))
                           AND JSON_CONTAINS_PATH(after_value, 'one', '$.address', '$.contact') = 0
                           AND value_schema_version = '2'
                           AND JSON_EXTRACT(after_value, '$.changedFields') = JSON_EXTRACT(?, '$')
                        FROM audit_record
                        WHERE action = 'STORE_PROFILE_UPDATED'
                        """).param(expectedChangedFields).query(Integer.class).single())
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM audit_record_target t
                        JOIN audit_record r ON r.audit_id = t.audit_id
                        WHERE r.action = 'STORE_PROFILE_UPDATED'
                          AND ((t.relation_type = 'PRIMARY' AND t.target_type = 'STORE_PROFILE')
                            OR (t.relation_type = 'STORE' AND t.target_type = 'STORE_PROFILE'))
                        """).query(Long.class).single())
                .isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(jdbcClient.sql("""
                        SELECT COUNT(*) FROM audit_record_target t
                        JOIN audit_record r ON r.audit_id = t.audit_id
                        WHERE r.action = 'STORE_PROFILE_UPDATED'
                        """).query(Long.class).single())
                .isEqualTo(2L);
    }

    private static String validBody(String name, String timeZone) {
        return """
                {"name":"%s","address":"서울시 중구","contact":"02-1234-5678","timeZone":"%s"}
                """.formatted(name, timeZone);
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
        FailingAuditWriter failingAuditWriter(
                @Qualifier("jpaAuditWriter") AuditWriter delegate) {
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
