package com.dorosoft.erp.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {
            "doro.store.bootstrap.enabled=false",
            "doro.erp.tenant-id=qr-store",
            "doro.erp.public-base-url=https://qr.example.test",
            "doro.table.qr-access.rate-limit.capacity=2",
            "doro.table.qr-access.rate-limit.window=PT1M",
            "audit.security.payload-hmac.key-base64=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=",
            "table.security.idempotency.encryption-key-base64=BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc="
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("TABLE-04 QR 공개 접근 API 통합 테스트")
class TableQrPublicAccessApiIntegrationTest {

    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(20);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블과_감사를_비운다() {
        TableIntegrationSupport.cleanAuditTables(jdbcClient);
        TableIntegrationSupport.cleanTableTables(jdbcClient);
    }

    @Test
    @DisplayName("유효한 Token이면 접근 가능한 테이블과 열린 Session 최소 정보를 반환한다")
    void 유효한_token_접근_성공() throws Exception {
        CreatedTable table = createTable("P1", "창가 QR", 4);
        String token = issueToken(table.tableId());
        UUID sessionId = UUID.randomUUID();
        TableIntegrationSupport.openUsageSession(jdbcClient, sessionId, table.tableId());

        MvcResult result = verify(token, nextIp())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accessible").value(true))
                .andExpect(jsonPath("$.store.tenantId").value("qr-store"))
                .andExpect(jsonPath("$.table.tableNumber").value("P1"))
                .andExpect(jsonPath("$.table.displayName").value("창가 QR"))
                .andExpect(jsonPath("$.session.sessionId").value(sessionId.toString()))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("credential", "token", "digest", "tableId");
    }

    @Test
    @DisplayName("존재하지 않는 Token은 Credential 존재 여부를 노출하지 않고 거부한다")
    void 존재하지_않는_token_거부() throws Exception {
        String token = randomToken();

        MvcResult result = verify(token, nextIp())
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("QR_ACCESS_DENIED"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
    }

    @Test
    @DisplayName("폐기된 Token은 거부한다")
    void 폐기된_token_거부() throws Exception {
        CreatedTable table = createTable("P2", "폐기", 4);
        String token = issueToken(table.tableId());
        TableIntegrationSupport.openUsageSession(jdbcClient, UUID.randomUUID(), table.tableId());
        revokeAllCredentials();

        verify(token, nextIp())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QR_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("재발급 전 기존 Token은 거부한다")
    void 재발급_전_기존_token_거부() throws Exception {
        CreatedTable table = createTable("P3", "재발급", 4);
        String oldToken = issueToken(table.tableId());
        reissueToken(table.tableId());
        TableIntegrationSupport.openUsageSession(jdbcClient, UUID.randomUUID(), table.tableId());

        verify(oldToken, nextIp())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QR_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("비활성 테이블의 Token은 거부한다")
    void 비활성_테이블_접근_거부() throws Exception {
        CreatedTable table = createTable("P4", "비활성", 4);
        String token = issueToken(table.tableId());
        TableIntegrationSupport.openUsageSession(jdbcClient, UUID.randomUUID(), table.tableId());
        updateActivation(table, false);

        verify(token, nextIp())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QR_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("현재 열린 Session이 없으면 거부한다")
    void 열린_session_없음_거부() throws Exception {
        CreatedTable table = createTable("P5", "빈 테이블", 4);
        String token = issueToken(table.tableId());

        verify(token, nextIp())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QR_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("허용되지 않은 Host 요청은 거부한다")
    void 허용되지_않은_host_거부() throws Exception {
        CreatedTable table = createTable("P6", "Host", 4);
        String token = issueToken(table.tableId());
        TableIntegrationSupport.openUsageSession(jdbcClient, UUID.randomUUID(), table.tableId());

        verify(token, nextIp(), "evil.example.test")
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("QR_HOST_FORBIDDEN"));
    }

    @Test
    @DisplayName("다른 테이블에 열린 Session이 있어도 Token이 연결된 테이블의 Session만 인정한다")
    void 다른_테이블_credential_접근_차단() throws Exception {
        CreatedTable credentialTable = createTable("P7", "Credential 테이블", 4);
        String token = issueToken(credentialTable.tableId());
        CreatedTable otherTable = createTable("P8", "다른 테이블", 4);
        TableIntegrationSupport.openUsageSession(jdbcClient, UUID.randomUUID(), otherTable.tableId());

        verify(token, nextIp())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QR_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("응답 Body와 Header에 Token 원문과 digest를 노출하지 않는다")
    void 응답에_token_digest_노출_없음(CapturedOutput output) throws Exception {
        CreatedTable table = createTable("P9", "민감정보", 4);
        String token = issueToken(table.tableId());
        TableIntegrationSupport.openUsageSession(jdbcClient, UUID.randomUUID(), table.tableId());
        String digestHex = digestHex(token);

        MvcResult result = verify(token, nextIp())
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String headers = result.getResponse().getHeaderNames().stream()
                .flatMap(name -> result.getResponse().getHeaders(name).stream().map(value -> name + ":" + value))
                .collect(Collectors.joining("\n"));
        assertThat(body).doesNotContain(token, digestHex, digestHex.toLowerCase(), "credentialId", "tokenDigest");
        assertThat(headers).doesNotContain(token, digestHex, digestHex.toLowerCase());
        assertThat(output).doesNotContain(token, digestHex, digestHex.toLowerCase());
    }

    @Test
    @DisplayName("호출 제한을 초과하면 요청을 거부한다")
    void 호출_제한_초과_거부() throws Exception {
        CreatedTable table = createTable("P10", "제한", 4);
        String token = issueToken(table.tableId());
        TableIntegrationSupport.openUsageSession(jdbcClient, UUID.randomUUID(), table.tableId());
        String clientIp = nextIp();

        verify(token, clientIp).andExpect(status().isOk());
        verify(token, clientIp).andExpect(status().isOk());
        verify(token, clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("QR_RATE_LIMITED"));
    }

    private org.springframework.test.web.servlet.ResultActions verify(String token, String clientIp) throws Exception {
        return verify(token, clientIp, "qr.example.test");
    }

    private org.springframework.test.web.servlet.ResultActions verify(String token, String clientIp, String host)
            throws Exception {
        return mockMvc.perform(
                post("/qr/table-access")
                        .with(remoteAddress(clientIp))
                        .header(HttpHeaders.HOST, host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private String issueToken(UUID tableId) throws Exception {
        JsonNode response =
                performJson(
                        post("/tables/{tableId}/qr-credentials", tableId)
                                .with(user("owner").roles("OWNER"))
                                .header("Idempotency-Key", UUID.randomUUID().toString()),
                        201);
        return tokenFromAccessUrl(response.get("accessUrl").asText());
    }

    private String reissueToken(UUID tableId) throws Exception {
        JsonNode response =
                performJson(
                        post("/tables/{tableId}/qr-credentials/reissue", tableId)
                                .with(user("owner").roles("OWNER"))
                                .header("Idempotency-Key", UUID.randomUUID().toString()),
                        201);
        return tokenFromAccessUrl(response.get("accessUrl").asText());
    }

    private CreatedTable createTable(String tableNumber, String displayName, int seatCapacity) throws Exception {
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
        return new CreatedTable(UUID.fromString(body.get("tableId").asText()), body.get("version").asLong());
    }

    private void updateActivation(CreatedTable table, boolean active) throws Exception {
        performJson(
                patch("/tables/{tableId}/activation", table.tableId())
                        .with(user("owner").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", table.version())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(Map.of("active", active))),
                200);
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

    private void revokeAllCredentials() {
        jdbcClient.sql(
                        """
                        UPDATE table_qr_credential
                           SET status = 'REVOKED',
                               revoked_by = :revokedBy,
                               revoked_at = CURRENT_TIMESTAMP(6)
                        """)
                .param("revokedBy", TableIntegrationSupport.uuidBytes(UUID.randomUUID()))
                .update();
    }

    private String tokenFromAccessUrl(String accessUrl) {
        URI uri = URI.create(accessUrl);
        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getFragment()).startsWith("token=");
        return uri.getFragment().substring("token=".length());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        System.arraycopy(TableIntegrationSupport.uuidBytes(first), 0, bytes, 0, 16);
        System.arraycopy(TableIntegrationSupport.uuidBytes(second), 0, bytes, 16, 16);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digestHex(String token) throws Exception {
        byte[] tokenBytes = Base64.getUrlDecoder().decode(token);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tokenBytes));
    }

    private String nextIp() {
        return "10.0.0." + IP_SEQUENCE.getAndIncrement();
    }

    private RequestPostProcessor remoteAddress(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    private record CreatedTable(UUID tableId, long version) {}
}
