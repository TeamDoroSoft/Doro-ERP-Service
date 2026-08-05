package com.dorosoft.erp.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.table.application.TableOperationObservability;
import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader;
import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader.CloseGuardQuery;
import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader.CloseGuardResult;
import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {
            "doro.store.bootstrap.enabled=false",
            "doro.erp.tenant-id=observability-store",
            "doro.erp.public-base-url=https://qr.example.test",
            "audit.security.payload-hmac.key-base64=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=",
            "table.security.idempotency.encryption-key-base64=BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc="
        })
@AutoConfigureMockMvc
@Import({
    TestcontainersConfiguration.class,
    TableAuditObservabilityIntegrationTest.CloseGuardTestConfiguration.class
})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("TABLE-08 Table·Session 감사와 관측성 통합 테스트")
class TableAuditObservabilityIntegrationTest {

    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(100);
    private static final Set<String> FORBIDDEN_METRIC_TAG_KEYS =
            Set.of("token", "digest", "requestId", "sessionId", "tableId", "actorId", "orderId", "credentialId");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private MutableCloseGuardReader closeGuardReader;

    @BeforeEach
    void 테이블과_감사를_비운다() {
        closeGuardReader.reset();
        TableIntegrationSupport.cleanAuditTables(jdbcClient);
        TableIntegrationSupport.cleanTableTables(jdbcClient);
    }

    @Test
    @DisplayName("Table 등록·변경·비활성화 감사에 작업자, 대상, 변경 전후, Request ID를 남긴다")
    void table_변경_감사_기록() throws Exception {
        CreatedTable table = createTable("A8", "감사 테이블", 4, "req-table-created");

        JsonNode updated =
                performJson(
                        put("/tables/{tableId}", table.tableId())
                                .with(user("manager").roles("MANAGER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Request-Id", "req-table-updated")
                                .header("If-Match", table.version())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", "A9",
                                                        "displayName", "변경 테이블",
                                                        "seatCapacity", 6))),
                        200);

        performJson(
                patch("/tables/{tableId}/activation", table.tableId())
                        .with(user("owner").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-table-deactivated")
                        .header("If-Match", updated.path("version").asLong())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(Map.of("active", false))),
                200);

        String created = auditPayload("TABLE_CREATED");
        String changed = auditPayload("TABLE_UPDATED");
        String deactivated = auditPayload("TABLE_ACTIVATION_CHANGED");

        assertThat(created)
                .contains(
                        "STORE",
                        "TABLE",
                        table.tableId().toString(),
                        "A8",
                        "감사 테이블",
                        "req-table-created");
        assertThat(changed)
                .contains(
                        "A8",
                        "A9",
                        "감사 테이블",
                        "변경 테이블",
                        "seatCapacity",
                        "req-table-updated");
        assertThat(deactivated)
                .contains("\"active\": true", "\"active\": false", "req-table-deactivated");
    }

    @Test
    @DisplayName("QR 발급·재발급·공개 검증 감사와 지표에 Token, digest, Fragment URL을 남기지 않는다")
    void qr_감사_관측성_민감정보_비노출(CapturedOutput output) throws Exception {
        CreatedTable table = createTable("Q8", "QR 감사", 4, "req-qr-table");
        double issueBefore = metricCount("qr.issue", "success", "none");
        double reissueBefore = metricCount("qr.reissue", "success", "none");
        double verifyBefore = metricCount("qr.verify", "success", "none");

        JsonNode issued = issue(table.tableId(), "req-qr-issued", 201);
        String accessUrl = issued.path("accessUrl").asText();
        String token = tokenFromAccessUrl(accessUrl);
        String digestHex = digestHex(token);
        JsonNode reissued = reissue(table.tableId(), "req-qr-rotated", 201);
        TableIntegrationSupport.openUsageSession(jdbcClient, UUID.randomUUID(), table.tableId());

        verify(tokenFromAccessUrl(reissued.path("accessUrl").asText()), nextIp())
                .andExpect(status().isOk());
        verify(token, nextIp())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QR_ACCESS_DENIED"));

        String issuedAudit = auditPayload("TABLE_QR_ISSUED");
        String rotatedAudit = auditPayload("TABLE_QR_ROTATED");
        String allAudit = issuedAudit + rotatedAudit;
        assertThat(issuedAudit).contains("credentialId", "ACTIVE", "req-qr-issued");
        assertThat(rotatedAudit)
                .contains(
                        issued.path("credentialId").asText(),
                        reissued.path("credentialId").asText(),
                        "predecessorCredentialId",
                        "req-qr-rotated");
        assertThat(allAudit + output)
                .doesNotContain(token, accessUrl, digestHex, digestHex.toLowerCase(), "tokenDigest");

        assertThat(metricCount("qr.issue", "success", "none")).isEqualTo(issueBefore + 1.0);
        assertThat(metricCount("qr.reissue", "success", "none")).isEqualTo(reissueBefore + 1.0);
        assertThat(metricCount("qr.verify", "success", "none")).isEqualTo(verifyBefore + 1.0);
        assertThat(metricCount("qr.verify", "failure", "QR_ACCESS_DENIED")).isGreaterThanOrEqualTo(1.0);
        assertMetricTagsDoNotContainIdsOrSecrets(token, digestHex);
    }

    @Test
    @DisplayName("Session 시작·종료·종료 차단을 감사와 지표로 추적한다")
    void session_감사_관측성() throws Exception {
        CreatedTable table = createTable("S8", "세션 감사", 4, "req-session-table");
        double startBefore = metricCount("session.start", "success", "none");
        double blockedBefore = metricCount("session.close", "blocked", "IN_PROGRESS_ORDER");

        JsonNode opened =
                performJson(
                        post("/tables/{tableId}/sessions", table.tableId())
                                .with(user("staff").roles("STAFF"))
                                .header("X-Request-Id", "req-session-opened")
                                .header("Idempotency-Key", UUID.randomUUID().toString()),
                        201);
        UUID sessionId = UUID.fromString(opened.path("sessionId").asText());
        closeGuardReader.block("IN_PROGRESS_ORDER");

        mockMvc
                .perform(
                        post("/tables/{tableId}/sessions/{sessionId}/close", table.tableId(), sessionId)
                                .with(user("staff").roles("STAFF"))
                                .header("X-Request-Id", "req-session-close-blocked")
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_CLOSE_BLOCKED"));

        String openedAudit = auditPayload("TABLE_SESSION_OPENED");
        String blockedAudit = auditPayload("TABLE_SESSION_CLOSE_BLOCKED");
        assertThat(openedAudit).contains(sessionId.toString(), table.tableId().toString(), "req-session-opened");
        assertThat(blockedAudit)
                .contains(
                        sessionId.toString(),
                        table.tableId().toString(),
                        "OPEN",
                        "IN_PROGRESS_ORDER",
                        "req-session-close-blocked");
        assertThat(auditCount("TABLE_SESSION_CLOSED")).isZero();
        assertThat(metricCount("session.start", "success", "none")).isEqualTo(startBefore + 1.0);
        assertThat(metricCount("session.close", "blocked", "IN_PROGRESS_ORDER")).isEqualTo(blockedBefore + 1.0);
    }

    @Test
    @DisplayName("감사 기록 수정·삭제 경로가 없고 일반 조회로 기존 감사가 변경되지 않는다")
    void 감사_불변성_확인() throws Exception {
        CreatedTable table = createTable("I8", "불변", 4, "req-immutable");
        String auditId = latestAuditId("TABLE_CREATED");
        String before = auditPayload("TABLE_CREATED");

        mockMvc.perform(put("/audit-records/{auditId}", auditId).with(user("owner").roles("OWNER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/audit-records/{auditId}", auditId).with(user("owner").roles("OWNER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/tables/{tableId}/sessions", table.tableId())
                        .with(user("customer").roles("CUSTOMER"))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        assertThat(auditPayload("TABLE_CREATED")).isEqualTo(before);
        assertThat(auditCount("TABLE_CREATED")).isEqualTo(1);
        assertThat(metricCount("session.start", "failure", "FORBIDDEN")).isGreaterThanOrEqualTo(1.0);
    }

    private CreatedTable createTable(String tableNumber, String displayName, int seatCapacity, String requestId)
            throws Exception {
        JsonNode body =
                performJson(
                        post("/tables")
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Request-Id", requestId)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", tableNumber,
                                                        "displayName", displayName,
                                                        "seatCapacity", seatCapacity))),
                        201);
        return new CreatedTable(UUID.fromString(body.path("tableId").asText()), body.path("version").asLong());
    }

    private JsonNode issue(UUID tableId, String requestId, int status) throws Exception {
        return performJson(
                post("/tables/{tableId}/qr-credentials", tableId)
                        .with(user("owner").roles("OWNER"))
                        .header("X-Request-Id", requestId)
                        .header("Idempotency-Key", UUID.randomUUID().toString()),
                status);
    }

    private JsonNode reissue(UUID tableId, String requestId, int status) throws Exception {
        return performJson(
                post("/tables/{tableId}/qr-credentials/reissue", tableId)
                        .with(user("owner").roles("OWNER"))
                        .header("X-Request-Id", requestId)
                        .header("Idempotency-Key", UUID.randomUUID().toString()),
                status);
    }

    private org.springframework.test.web.servlet.ResultActions verify(String token, String clientIp) throws Exception {
        return mockMvc.perform(
                post("/qr/table-access")
                        .with(remoteAddress(clientIp))
                        .header(HttpHeaders.HOST, "qr.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private JsonNode performJson(MockHttpServletRequestBuilder requestBuilder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(requestBuilder).andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String auditPayload(String action) {
        return jdbcClient.sql(
                        """
                        SELECT CONCAT(
                                   audit_id, '|', action, '|', domain, '|', target_type, '|', target_id, '|',
                                   before_value, '|', after_value, '|',
                                   COALESCE(reason_code, ''), '|', COALESCE(reason, ''), '|',
                                   actor_id, '|', request_id
                               )
                          FROM audit_record
                         WHERE action = :action
                         ORDER BY occurred_at DESC, audit_id DESC
                         LIMIT 1
                        """)
                .param("action", action)
                .query(String.class)
                .single();
    }

    private String latestAuditId(String action) {
        return jdbcClient.sql(
                        """
                        SELECT audit_id
                          FROM audit_record
                         WHERE action = :action
                         ORDER BY occurred_at DESC, audit_id DESC
                         LIMIT 1
                        """)
                .param("action", action)
                .query(String.class)
                .single();
    }

    private long auditCount(String action) {
        return jdbcClient.sql("SELECT COUNT(*) FROM audit_record WHERE action = :action")
                .param("action", action)
                .query(Long.class)
                .single();
    }

    private double metricCount(String operation, String result, String reason) {
        var counter =
                meterRegistry
                        .find(TableOperationObservability.METRIC_NAME)
                        .tag("operation", operation)
                        .tag("result", result)
                        .tag("reason", reason)
                        .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private void assertMetricTagsDoNotContainIdsOrSecrets(String token, String digestHex) {
        meterRegistry.getMeters().stream()
                .filter(meter -> TableOperationObservability.METRIC_NAME.equals(meter.getId().getName()))
                .forEach(
                        meter ->
                                meter.getId()
                                        .getTags()
                                        .forEach(
                                                tag -> {
                                                    assertThat(FORBIDDEN_METRIC_TAG_KEYS)
                                                            .doesNotContain(tag.getKey());
                                                    assertThat(tag.getValue())
                                                            .doesNotContain(
                                                                    token,
                                                                    digestHex,
                                                                    digestHex.toLowerCase(),
                                                                    "qr.example.test/qr#token=");
                                                }));
    }

    private String tokenFromAccessUrl(String accessUrl) {
        URI uri = URI.create(accessUrl);
        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getFragment()).startsWith("token=");
        return uri.getFragment().substring("token=".length());
    }

    private String digestHex(String token) throws Exception {
        byte[] tokenBytes = Base64.getUrlDecoder().decode(token);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tokenBytes));
    }

    private String nextIp() {
        return "10.8.0." + IP_SEQUENCE.getAndIncrement();
    }

    private RequestPostProcessor remoteAddress(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    private record CreatedTable(UUID tableId, long version) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class CloseGuardTestConfiguration {

        @Bean
        @Primary
        MutableCloseGuardReader mutableCloseGuardReader() {
            return new MutableCloseGuardReader();
        }
    }

    static class MutableCloseGuardReader implements TableSessionCloseGuardReader {

        private volatile String blockerCode;

        @Override
        public CloseGuardResult inspect(CloseGuardQuery query) {
            if (blockerCode == null) {
                return CloseGuardResult.closable();
            }
            return CloseGuardResult.blocked(blockerCode, "Close guard blocked.");
        }

        void block(String code) {
            this.blockerCode = code;
        }

        void reset() {
            this.blockerCode = null;
        }
    }
}
