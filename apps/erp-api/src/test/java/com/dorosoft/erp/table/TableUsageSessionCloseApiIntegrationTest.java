package com.dorosoft.erp.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader;
import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader.CloseGuardQuery;
import com.dorosoft.erp.table.application.port.TableSessionCloseGuardReader.CloseGuardResult;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import({TestcontainersConfiguration.class, TableUsageSessionCloseApiIntegrationTest.CloseGuardTestConfiguration.class})
@DisplayName("TABLE-06 테이블 이용 Session 종료 Guard API 통합 테스트")
class TableUsageSessionCloseApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private MutableCloseGuardReader closeGuardReader;

    @BeforeEach
    void 테이블과_감사를_비운다() {
        closeGuardReader.reset();
        TableIntegrationSupport.cleanAuditTables(jdbcClient);
        TableIntegrationSupport.cleanTableTables(jdbcClient);
    }

    @Test
    @DisplayName("미완료 주문과 미정산 결제가 없으면 열린 Session을 종료한다")
    void 종료_성공() throws Exception {
        OpenedSession session = openSession("C1", "정상");

        JsonNode response = closeSession(session, UUID.randomUUID().toString(), staff(), 200);

        assertThat(response.get("sessionId").asText()).isEqualTo(session.sessionId().toString());
        assertThat(response.get("tableId").asText()).isEqualTo(session.tableId().toString());
        assertThat(response.get("status").asText()).isEqualTo("CLOSED");
        assertThat(response.get("openedAt").asText()).isNotBlank();
        assertThat(response.get("closedAt").asText()).isNotBlank();
        assertThat(openSessionCount(session.tableId())).isZero();
        assertThat(closedSessionCount(session.sessionId())).isEqualTo(1);
    }

    @Test
    @DisplayName("직원 권한이 없으면 Session 종료를 거부한다")
    void 권한_없는_요청_거부() throws Exception {
        OpenedSession session = openSession("C2", "권한");

        mockMvc
                .perform(
                        closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString())
                                .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 Session 요청은 실패하고 열린 Session을 남기지 않는다")
    void 존재하지_않는_session_요청_실패() throws Exception {
        CreatedTable table = createTable("C3", "없음", 4);

        mockMvc
                .perform(
                        closeRequest(table.tableId(), UUID.randomUUID(), UUID.randomUUID().toString())
                                .with(staff()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_NOT_FOUND"));

        assertThat(openSessionCount(table.tableId())).isZero();
    }

    @Test
    @DisplayName("이미 종료된 Session은 새 종료 요청으로 다시 종료하지 않는다")
    void 이미_종료된_session_처리() throws Exception {
        OpenedSession session = openSession("C4", "종료됨");
        closeSession(session, UUID.randomUUID().toString(), staff(), 200);

        mockMvc
                .perform(closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString()).with(staff()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_ALREADY_CLOSED"));

        assertThat(closedSessionCount(session.sessionId())).isEqualTo(1);
        assertThat(closeAuditCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("진행 중 주문이 있으면 Session 종료를 차단한다")
    void 진행중_주문_종료_차단() throws Exception {
        OpenedSession session = openSession("C5", "주문");
        closeGuardReader.block("IN_PROGRESS_ORDER", "In-progress order remains.");

        mockMvc
                .perform(closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString()).with(staff()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_CLOSE_BLOCKED"))
                .andExpect(jsonPath("$.blockers[0].code").value("IN_PROGRESS_ORDER"));

        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
        assertThat(closeAuditCount()).isZero();
    }

    @Test
    @DisplayName("미결제 또는 미정산 결제가 있으면 Session 종료를 차단한다")
    void 미정산_결제_종료_차단() throws Exception {
        OpenedSession session = openSession("C6", "결제");
        closeGuardReader.block("UNSETTLED_PAYMENT", "Unsettled payment remains.");

        mockMvc
                .perform(closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString()).with(staff()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_CLOSE_BLOCKED"))
                .andExpect(jsonPath("$.blockers[0].code").value("UNSETTLED_PAYMENT"));

        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("주문·결제 상태를 판정할 수 없으면 Session 종료를 차단한다")
    void 주문_결제_상태_판정불가_종료_차단() throws Exception {
        OpenedSession session = openSession("C7", "판정");
        closeGuardReader.block("UNKNOWN_ORDER_OR_PAYMENT_STATE", "Order or payment state can not be determined.");

        mockMvc
                .perform(closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString()).with(staff()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_CLOSE_BLOCKED"))
                .andExpect(jsonPath("$.blockers[0].code").value("UNKNOWN_ORDER_OR_PAYMENT_STATE"));

        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 업체 Principal의 Session 접근은 차단하고 Session 정보를 노출하지 않는다")
    void 다른_업체_session_접근_차단() throws Exception {
        OpenedSession session = openSession("C8", "업체");

        MvcResult result =
                mockMvc
                        .perform(
                                closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString())
                                        .with(identityStaff("other-store", "STAFF")))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("업체", "CLOSED");
        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 재요청은 기존 종료 결과를 반환하고 중복 종료하지 않는다")
    void 같은_멱등키_재요청_중복_종료_방지() throws Exception {
        OpenedSession session = openSession("C9", "멱등");
        String idempotencyKey = UUID.randomUUID().toString();

        JsonNode first = closeSession(session, idempotencyKey, staff(), 200);
        JsonNode second = closeSession(session, idempotencyKey, staff(), 200);

        assertThat(second.get("sessionId").asText()).isEqualTo(first.get("sessionId").asText());
        assertThat(second.get("closedAt").asText()).isEqualTo(first.get("closedAt").asText());
        assertThat(closedSessionCount(session.sessionId())).isEqualTo(1);
        assertThat(closeAuditCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 요청이 동시에 같은 Session을 종료해도 종료 처리는 한 번만 수행된다")
    void 동시_session_종료_한번만_수행() throws Exception {
        OpenedSession session = openSession("C10", "동시");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> concurrentClose(session, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentClose(session, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        assertThat(openSessionCount(session.tableId())).isZero();
        assertThat(closedSessionCount(session.sessionId())).isEqualTo(1);
        assertThat(closeAuditCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("종료와 새 주문 추가가 경합하면 Session을 잘못 종료하지 않는다")
    void 종료와_새_주문_추가_경합_차단() throws Exception {
        OpenedSession session = openSession("C11", "경합");
        closeGuardReader.block("CONCURRENT_ORDER_APPEND", "A new order was appended during close guard.");

        mockMvc
                .perform(closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString()).with(staff()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_CLOSE_BLOCKED"))
                .andExpect(jsonPath("$.blockers[0].code").value("CONCURRENT_ORDER_APPEND"));

        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
        assertThat(closeAuditCount()).isZero();
    }

    @Test
    @DisplayName("Session 저장 실패 시 종료를 롤백한다")
    void session_저장_실패_롤백() throws Exception {
        OpenedSession session = openSession("C12", "저장");
        forceFutureOpenedAt(session.sessionId());

        mockMvc
                .perform(closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString()).with(staff()))
                .andExpect(status().is5xxServerError());

        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
        assertThat(closedSessionCount(session.sessionId())).isZero();
        assertThat(closeAuditCount()).isZero();
    }

    @Test
    @DisplayName("주문·결제 조회 실패 시 종료를 롤백한다")
    void 주문_결제_조회_실패_롤백() throws Exception {
        OpenedSession session = openSession("C13", "조회실패");
        closeGuardReader.fail();

        mockMvc
                .perform(closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString()).with(staff()))
                .andExpect(status().is5xxServerError());

        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
        assertThat(closedSessionCount(session.sessionId())).isZero();
    }

    @Test
    @DisplayName("감사 기록 실패 시 종료를 롤백한다")
    void 감사_기록_실패_롤백() throws Exception {
        OpenedSession session = openSession("C14", "감사실패");

        mockMvc
                .perform(
                        closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString())
                                .with(identityStaff("session-store", "ROLE_NAME_THAT_IS_LONGER_THAN_FORTY_CHARACTERS")))
                .andExpect(status().is5xxServerError());

        assertThat(openSessionCount(session.tableId())).isEqualTo(1);
        assertThat(closedSessionCount(session.sessionId())).isZero();
        assertThat(closeAuditCount()).isZero();
    }

    @Test
    @DisplayName("Session 종료 감사 기록을 남긴다")
    void session_종료_감사_기록() throws Exception {
        OpenedSession session = openSession("C15", "감사");
        String requestId = "req-table-session-close";

        closeSessionWithHeader(session, UUID.randomUUID().toString(), requestId, staff(), 200);

        String audit =
                jdbcClient.sql(
                                """
                                SELECT CONCAT(action, '|', domain, '|', target_type, '|', target_id, '|', after_value, '|', request_id)
                                  FROM audit_record
                                 WHERE action = 'TABLE_SESSION_CLOSED'
                                """)
                        .query(String.class)
                        .single();
        assertThat(audit)
                .contains(
                        "TABLE_SESSION_CLOSED",
                        "ORDER",
                        "TABLE_SESSION",
                        session.sessionId().toString(),
                        session.tableId().toString(),
                        "CLOSED",
                        requestId);
    }

    private int concurrentClose(OpenedSession session, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return mockMvc
                .perform(
                        closeRequest(session.tableId(), session.sessionId(), UUID.randomUUID().toString())
                                .with(staff()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private JsonNode closeSession(
            OpenedSession session, String idempotencyKey, RequestPostProcessor actor, int expectedStatus)
            throws Exception {
        return performJson(closeRequest(session.tableId(), session.sessionId(), idempotencyKey).with(actor), expectedStatus);
    }

    private JsonNode closeSessionWithHeader(
            OpenedSession session,
            String idempotencyKey,
            String requestId,
            RequestPostProcessor actor,
            int expectedStatus)
            throws Exception {
        return performJson(
                closeRequest(session.tableId(), session.sessionId(), idempotencyKey)
                        .with(actor)
                        .header("X-Request-Id", requestId),
                expectedStatus);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder closeRequest(
            UUID tableId, UUID sessionId, String idempotencyKey) {
        return post("/tables/{tableId}/sessions/{sessionId}/close", tableId, sessionId)
                .header("Idempotency-Key", idempotencyKey);
    }

    private OpenedSession openSession(String tableNumber, String displayName) throws Exception {
        CreatedTable table = createTable(tableNumber, displayName, 4);
        JsonNode response =
                performJson(
                        post("/tables/{tableId}/sessions", table.tableId())
                                .with(staff())
                                .header("Idempotency-Key", UUID.randomUUID().toString()),
                        201);
        return new OpenedSession(table.tableId(), UUID.fromString(response.get("sessionId").asText()));
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
        return new CreatedTable(UUID.fromString(body.get("tableId").asText()));
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

    private RequestPostProcessor staff() {
        return user("staff").roles("STAFF");
    }

    private RequestPostProcessor identityStaff(String tenantId, String roleCode) {
        UUID accountId = UUID.randomUUID();
        IdentityPrincipal principal =
                new IdentityPrincipal(accountId, tenantId, roleCode, Set.of("table.session.manage"), false);
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        principal, "N/A", List.of(new SimpleGrantedAuthority("table.session.manage"))));
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

    private long closedSessionCount(UUID sessionId) {
        return jdbcClient.sql(
                        """
                        SELECT COUNT(*)
                          FROM table_usage_session
                         WHERE session_id = :sessionId
                           AND status = 'CLOSED'
                        """)
                .param("sessionId", TableIntegrationSupport.uuidBytes(sessionId))
                .query(Long.class)
                .single();
    }

    private long closeAuditCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM audit_record WHERE action = 'TABLE_SESSION_CLOSED'")
                .query(Long.class)
                .single();
    }

    private void forceFutureOpenedAt(UUID sessionId) {
        jdbcClient.sql(
                        """
                        UPDATE table_usage_session
                           SET opened_at = DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)
                         WHERE session_id = :sessionId
                        """)
                .param("sessionId", TableIntegrationSupport.uuidBytes(sessionId))
                .update();
    }

    private record CreatedTable(UUID tableId) {}

    private record OpenedSession(UUID tableId, UUID sessionId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class CloseGuardTestConfiguration {

        @Bean
        @Primary
        MutableCloseGuardReader mutableCloseGuardReader() {
            return new MutableCloseGuardReader();
        }
    }

    static class MutableCloseGuardReader implements TableSessionCloseGuardReader {

        private final AtomicReference<CloseGuardResult> result = new AtomicReference<>(CloseGuardResult.closable());
        private final AtomicBoolean fail = new AtomicBoolean(false);

        @Override
        public CloseGuardResult inspect(CloseGuardQuery query) {
            if (fail.get()) {
                throw new IllegalStateException("Order payment close guard unavailable");
            }
            return result.get();
        }

        void block(String code, String message) {
            result.set(CloseGuardResult.blocked(code, message));
        }

        void fail() {
            fail.set(true);
        }

        void reset() {
            fail.set(false);
            result.set(CloseGuardResult.closable());
        }
    }
}
