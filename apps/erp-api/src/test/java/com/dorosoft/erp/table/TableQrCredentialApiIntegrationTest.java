package com.dorosoft.erp.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {
            "doro.store.bootstrap.enabled=false",
            "doro.erp.public-base-url=https://qr.example.test",
            "audit.security.payload-hmac.key-base64=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM="
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("TABLE-03 QR 자격증명 API 통합 테스트")
class TableQrCredentialApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블과_감사를_비운다() {
        TableIntegrationSupport.cleanAuditTables(jdbcClient);
        TableIntegrationSupport.cleanTableTables(jdbcClient);
    }

    @Test
    @DisplayName("관리자는 등록된 테이블의 QR 자격증명을 최초 발급할 수 있다")
    void 최초_발급_성공() throws Exception {
        UUID tableId = createTable("Q1", "QR 1", 4);

        JsonNode response = issue(tableId, UUID.randomUUID().toString(), 201);

        assertThat(response.get("credentialId").asText()).isNotBlank();
        assertThat(response.get("tableId").asText()).isEqualTo(tableId.toString());
        assertThat(response.get("status").asText()).isEqualTo("ACTIVE");
        String token = tokenFromAccessUrl(response.get("accessUrl").asText());
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
        assertThat(activeCredentialCount()).isEqualTo(1);
        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_qr_credential")).isEqualTo(1);
    }

    @Test
    @DisplayName("재발급은 기존 활성 Credential을 폐기하고 새 Credential 하나만 활성으로 유지한다")
    void 재발급_성공과_기존_폐기() throws Exception {
        UUID tableId = createTable("Q2", "QR 2", 4);
        JsonNode issued = issue(tableId, UUID.randomUUID().toString(), 201);

        JsonNode reissued = reissue(tableId, UUID.randomUUID().toString(), 201);

        assertThat(reissued.get("credentialId").asText()).isNotEqualTo(issued.get("credentialId").asText());
        assertThat(reissued.get("predecessorCredentialId").asText()).isEqualTo(issued.get("credentialId").asText());
        assertThat(activeCredentialCount()).isEqualTo(1);
        assertThat(revokedCredentialCount()).isEqualTo(1);
        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_qr_credential")).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 새 Credential과 새 Token을 만들지 않는다")
    void 같은_멱등키_중복_요청_방지() throws Exception {
        UUID tableId = createTable("Q3", "QR 3", 4);
        String idempotencyKey = UUID.randomUUID().toString();

        JsonNode first = issue(tableId, idempotencyKey, 201);
        String token = tokenFromAccessUrl(first.get("accessUrl").asText());
        JsonNode second = issue(tableId, idempotencyKey, 201);

        assertThat(second.get("credentialId").asText()).isEqualTo(first.get("credentialId").asText());
        assertThat(second.has("accessUrl")).isFalse();
        assertThat(second.toString()).doesNotContain(token);
        assertThat(activeCredentialCount()).isEqualTo(1);
        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_qr_credential")).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 재발급 요청이 들어와도 활성 Credential은 하나만 유지한다")
    void 동시_재발급_활성_중복_방지() throws Exception {
        UUID tableId = createTable("Q4", "QR 4", 4);
        issue(tableId, UUID.randomUUID().toString(), 201);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> concurrentReissue(tableId, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentReissue(tableId, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 201);
        } finally {
            executor.shutdownNow();
        }

        assertThat(activeCredentialCount()).isEqualTo(1);
        assertThat(revokedCredentialCount()).isEqualTo(2);
        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_qr_credential")).isEqualTo(3);
    }

    @Test
    @DisplayName("관리자 권한이 없으면 QR 자격증명 발급을 거부한다")
    void 권한_없는_요청_거부() throws Exception {
        UUID tableId = createTable("Q5", "QR 5", 4);

        mockMvc
                .perform(
                        post("/tables/{tableId}/qr-credentials", tableId)
                                .with(user("staff").roles("STAFF"))
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_qr_credential")).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 테이블의 QR 발급 요청은 실패하고 Credential을 남기지 않는다")
    void 존재하지_않는_테이블_요청_실패() throws Exception {
        mockMvc
                .perform(
                        post("/tables/{tableId}/qr-credentials", UUID.randomUUID())
                                .with(user("owner").roles("OWNER"))
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"));

        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_qr_credential")).isZero();
    }

    @Test
    @DisplayName("이미 활성 Credential이 있으면 최초 발급을 거부하고 불완전 Credential을 남기지 않는다")
    void 활성_credential_중복_발급_실패시_불완전_데이터_없음() throws Exception {
        UUID tableId = createTable("Q6", "QR 6", 4);
        issue(tableId, UUID.randomUUID().toString(), 201);

        mockMvc
                .perform(
                        post("/tables/{tableId}/qr-credentials", tableId)
                                .with(user("owner").roles("OWNER"))
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QR_CREDENTIAL_ALREADY_ACTIVE"));

        assertThat(activeCredentialCount()).isEqualTo(1);
        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_qr_credential")).isEqualTo(1);
    }

    @Test
    @DisplayName("Token 원문과 접속 URL은 DB, 감사 값, 로그에 남지 않는다")
    void 토큰_원문_비저장과_감사_로그_마스킹(CapturedOutput output) throws Exception {
        UUID tableId = createTable("Q7", "QR 7", 4);
        String idempotencyKey = UUID.randomUUID().toString();

        JsonNode response = issue(tableId, idempotencyKey, 201);
        String accessUrl = response.get("accessUrl").asText();
        String token = tokenFromAccessUrl(accessUrl);

        String credentialDigest =
                jdbcClient.sql("SELECT HEX(token_digest) FROM table_qr_credential")
                        .query(String.class)
                        .single();
        assertThat(credentialDigest).hasSize(64).doesNotContain(token);

        String idempotencyBody =
                jdbcClient.sql("SELECT response_body FROM table_idempotency_record WHERE idempotency_key = :key")
                        .param("key", idempotencyKey)
                        .query(String.class)
                        .single();
        assertThat(idempotencyBody).doesNotContain(token, accessUrl, "accessUrl");

        String auditBody =
                jdbcClient.sql(
                                """
                                SELECT CONCAT(before_value, after_value, COALESCE(reason, ''))
                                  FROM audit_record
                                 WHERE action = 'TABLE_QR_ISSUED'
                                  """)
                        .query(String.class)
                        .single();
        assertThat(auditBody)
                .contains("credentialId", "status")
                .doesNotContain(token, accessUrl, "token", "accessUrl");
        assertThat(output).doesNotContain(token, accessUrl);
    }

    private int concurrentReissue(UUID tableId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return mockMvc
                .perform(
                        post("/tables/{tableId}/qr-credentials/reissue", tableId)
                                .with(user("owner").roles("OWNER"))
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private JsonNode issue(UUID tableId, String idempotencyKey, int expectedStatus) throws Exception {
        return performJson(
                post("/tables/{tableId}/qr-credentials", tableId)
                        .with(user("owner").roles("OWNER"))
                        .header("Idempotency-Key", idempotencyKey),
                expectedStatus);
    }

    private JsonNode reissue(UUID tableId, String idempotencyKey, int expectedStatus) throws Exception {
        return performJson(
                post("/tables/{tableId}/qr-credentials/reissue", tableId)
                        .with(user("manager").roles("MANAGER"))
                        .header("Idempotency-Key", idempotencyKey),
                expectedStatus);
    }

    private UUID createTable(String tableNumber, String displayName, int seatCapacity) throws Exception {
        JsonNode body =
                performJson(
                        post("/tables")
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", tableNumber,
                                                        "displayName", displayName,
                                                        "seatCapacity", seatCapacity))),
                        201);
        return UUID.fromString(body.get("tableId").asText());
    }

    private JsonNode performJson(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder,
            int expectedStatus)
            throws Exception {
        MvcResult result =
                mockMvc.perform(requestBuilder)
                        .andExpect(status().is(expectedStatus))
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String tokenFromAccessUrl(String accessUrl) {
        URI uri = URI.create(accessUrl);
        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getFragment()).startsWith("token=");
        assertThat(uri.toString()).startsWith("https://qr.example.test/qr#token=");
        return uri.getFragment().substring("token=".length());
    }

    private long activeCredentialCount() {
        return credentialCount("ACTIVE");
    }

    private long revokedCredentialCount() {
        return credentialCount("REVOKED");
    }

    private long credentialCount(String status) {
        return jdbcClient.sql("SELECT COUNT(*) FROM table_qr_credential WHERE status = :status")
                .param("status", status)
                .query(Long.class)
                .single();
    }
}
