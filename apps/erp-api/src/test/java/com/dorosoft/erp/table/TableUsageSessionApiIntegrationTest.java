package com.dorosoft.erp.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {
            "doro.store.bootstrap.enabled=false",
            "doro.erp.tenant-id=session-store",
            "audit.security.payload-hmac.key-base64=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=",
            "table.security.idempotency.encryption-key-base64=BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc="
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("TABLE-05 테이블 이용 Session 시작 API 통합 테스트")
class TableUsageSessionApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블과_감사를_비운다() {
        TableIntegrationSupport.cleanAuditTables(jdbcClient);
        TableIntegrationSupport.cleanTableTables(jdbcClient);
    }

    @Test
    @DisplayName("직원은 활성 테이블의 Session을 시작할 수 있다")
    void 활성_테이블_session_시작_성공() throws Exception {
        CreatedTable table = createTable("S1", "창가", 4);

        JsonNode response = startSession(table.tableId(), UUID.randomUUID().toString(), staff(), 201);

        assertThat(response.get("sessionId").asText()).isNotBlank();
        assertThat(response.get("tableId").asText()).isEqualTo(table.tableId().toString());
        assertThat(response.get("status").asText()).isEqualTo("OPEN");
        assertThat(response.get("openedAt").asText()).isNotBlank();
        assertThat(openSessionCount(table.tableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("직원 권한이 없으면 Session 시작을 거부한다")
    void 직원_권한_없음_거부() throws Exception {
        CreatedTable table = createTable("S2", "권한", 4);

        mockMvc
                .perform(
                        post("/tables/{tableId}/sessions", table.tableId())
                                .with(user("customer").roles("CUSTOMER"))
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        assertThat(openSessionCount(table.tableId())).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 테이블 요청은 실패하고 Session을 남기지 않는다")
    void 존재하지_않는_테이블_실패() throws Exception {
        mockMvc
                .perform(
                        post("/tables/{tableId}/sessions", UUID.randomUUID())
                                .with(staff())
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"));

        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_usage_session")).isZero();
    }

    @Test
    @DisplayName("비활성 테이블은 Session을 시작할 수 없다")
    void 비활성_테이블_실패() throws Exception {
        CreatedTable table = createTable("S3", "비활성", 4);
        updateActivation(table, false);

        mockMvc
                .perform(
                        post("/tables/{tableId}/sessions", table.tableId())
                                .with(staff())
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_NOT_AVAILABLE"));

        assertThat(openSessionCount(table.tableId())).isZero();
    }

    @Test
    @DisplayName("이미 열린 Session이 있는 테이블은 새 Session을 만들 수 없다")
    void 이미_열린_session_있음_실패() throws Exception {
        CreatedTable table = createTable("S4", "중복", 4);
        startSession(table.tableId(), UUID.randomUUID().toString(), staff(), 201);

        mockMvc
                .perform(
                        post("/tables/{tableId}/sessions", table.tableId())
                                .with(staff())
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_ALREADY_OPEN"));

        assertThat(openSessionCount(table.tableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 기존 시작 결과를 반환하고 중복 생성하지 않는다")
    void 같은_멱등키_재요청_중복_방지() throws Exception {
        CreatedTable table = createTable("S5", "멱등", 4);
        String idempotencyKey = UUID.randomUUID().toString();

        JsonNode first = startSession(table.tableId(), idempotencyKey, staff(), 201);
        JsonNode second = startSession(table.tableId(), idempotencyKey, staff(), 201);

        assertThat(second.get("sessionId").asText()).isEqualTo(first.get("sessionId").asText());
        assertThat(openSessionCount(table.tableId())).isEqualTo(1);
        assertThat(TableIntegrationSupport.countOf(jdbcClient, "table_idempotency_record")).isEqualTo(2);
    }

    @Test
    @DisplayName("서로 다른 요청이 동시에 같은 테이블의 Session을 시작해도 열린 Session은 하나만 유지된다")
    void 동시_session_시작_열린_session_하나_유지() throws Exception {
        CreatedTable table = createTable("S6", "동시", 4);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> concurrentStart(table.tableId(), ready, start));
            Future<Integer> second = executor.submit(() -> concurrentStart(table.tableId(), ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }

        assertThat(openSessionCount(table.tableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 업체 Principal의 테이블 접근은 차단하고 테이블 정보를 노출하지 않는다")
    void 다른_업체_테이블_접근_차단() throws Exception {
        CreatedTable table = createTable("S7", "업체", 4);

        MvcResult result =
                mockMvc
                        .perform(
                                post("/tables/{tableId}/sessions", table.tableId())
                                        .with(identityStaff("other-store", "STAFF"))
                                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("업체", "OPEN");
        assertThat(openSessionCount(table.tableId())).isZero();
    }

    @Test
    @DisplayName("Session 시작 감사 기록을 남긴다")
    void session_시작_감사_기록() throws Exception {
        CreatedTable table = createTable("S8", "감사", 4);
        String requestId = "req-table-session-open";

        MvcResult result =
                mockMvc.perform(
                                post("/tables/{tableId}/sessions", table.tableId())
                                        .with(staff())
                                        .header("X-Request-Id", requestId)
                                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andExpect(status().isCreated())
                        .andReturn();
        String sessionId = objectMapper.readTree(result.getResponse().getContentAsString()).get("sessionId").asText();

        String audit =
                jdbcClient.sql(
                                """
                                SELECT CONCAT(action, '|', domain, '|', target_type, '|', target_id, '|', after_value, '|', request_id)
                                  FROM audit_record
                                 WHERE action = 'TABLE_SESSION_OPENED'
                                """)
                        .query(String.class)
                        .single();
        assertThat(audit)
                .contains("TABLE_SESSION_OPENED", "ORDER", "TABLE_SESSION", sessionId, table.tableId().toString(), requestId);
    }

    @Test
    @DisplayName("처리 중 실패하면 불완전한 Session을 남기지 않는다")
    void 처리중_실패_session_롤백() throws Exception {
        CreatedTable table = createTable("S9", "롤백", 4);

        mockMvc
                .perform(
                        post("/tables/{tableId}/sessions", table.tableId())
                                .with(identityStaff("session-store", "ROLE_NAME_THAT_IS_LONGER_THAN_FORTY_CHARACTERS"))
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().is5xxServerError());

        assertThat(openSessionCount(table.tableId())).isZero();
    }

    private int concurrentStart(UUID tableId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return mockMvc
                .perform(
                        post("/tables/{tableId}/sessions", tableId)
                                .with(staff())
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private JsonNode startSession(UUID tableId, String idempotencyKey, RequestPostProcessor actor, int expectedStatus)
            throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/tables/{tableId}/sessions", tableId)
                                        .with(actor)
                                        .header("Idempotency-Key", idempotencyKey))
                        .andExpect(status().is(expectedStatus))
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    private long openSessionCount(UUID tableId) {
        return jdbcClient.sql(
                        """
                        SELECT COUNT(*)
                          FROM table_usage_session
                         WHERE table_id = :tableId
                           AND status = 'OPEN'
                        """)
                .param("tableId", TableIntegrationSupport.uuidBytes(tableId))
                .query(Long.class)
                .single();
    }

    private RequestPostProcessor staff() {
        return user("staff").roles("STAFF");
    }

    private RequestPostProcessor identityStaff(String tenantId, String roleCode) {
        UUID accountId = UUID.randomUUID();
        IdentityPrincipal principal =
                new IdentityPrincipal(accountId, tenantId, roleCode, Set.of("table.session.manage"), false);
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        "n/a",
                        List.of(new SimpleGrantedAuthority("table.session.manage"))));
    }

    private record CreatedTable(UUID tableId, long version) {}
}
