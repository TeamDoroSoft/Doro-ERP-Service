package com.dorosoft.erp.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.table.application.TableErrorCode;
import com.dorosoft.erp.table.application.TableManagementException;
import com.dorosoft.erp.table.application.dto.TableOrderItemSummaryResponse;
import com.dorosoft.erp.table.application.dto.TableOrderSummaryResponse;
import com.dorosoft.erp.table.application.port.TableSessionOrderReader;
import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {
            "doro.store.bootstrap.enabled=false",
            "doro.erp.tenant-id=session-store",
            "audit.security.payload-hmac.key-base64=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM="
        })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TableOrderQueryApiIntegrationTest.OrderReaderTestConfiguration.class})
@DisplayName("TABLE-07 테이블 현재·과거 주문 조회 API 통합 테스트")
class TableOrderQueryApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private MutableOrderReader orderReader;

    @BeforeEach
    void 테이블과_감사를_비운다() {
        orderReader.clear();
        TableIntegrationSupport.cleanAuditTables(jdbcClient);
        TableIntegrationSupport.cleanTableTables(jdbcClient);
    }

    @Test
    @DisplayName("현재 열린 Session의 주문을 조회한다")
    void 현재_열린_session_주문_조회_성공() throws Exception {
        OpenedSession session = openSession("O1", "현재");
        orderReader.add(session.sessionId(), order("A-001", "COMPLETED", "PAID", "12000.00"));
        orderReader.add(session.sessionId(), order("A-002", "IN_PROGRESS", "UNPAID", "9000.00"));

        JsonNode response =
                performJson(
                        get("/tables/{tableId}/sessions/current/orders", session.tableId())
                                .with(staff())
                                .param("status", "COMPLETED")
                                .param("size", "10"),
                        200);

        assertThat(response.path("session").path("sessionId").asText()).isEqualTo(session.sessionId().toString());
        assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("orderNumber").asText()).isEqualTo("A-001");
        assertThat(response.path("items").get(0).path("totalAmount").decimalValue()).isEqualByComparingTo("12000.00");
        assertThat(response.path("items").get(0).path("paymentStatus").asText()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("현재 Session에 주문이 없으면 빈 목록을 반환한다")
    void 현재_session_주문_없음_빈목록() throws Exception {
        OpenedSession session = openSession("O2", "빈주문");

        JsonNode response =
                performJson(get("/tables/{tableId}/sessions/current/orders", session.tableId()).with(staff()), 200);

        assertThat(response.path("session").path("sessionId").asText()).isEqualTo(session.sessionId().toString());
        assertThat(response.path("items")).isEmpty();
        assertThat(response.path("nextCursor").isNull()).isTrue();
    }

    @Test
    @DisplayName("열린 Session이 없는 테이블의 현재 주문 조회는 빈 결과를 반환한다")
    void 열린_session_없는_현재_주문_빈결과() throws Exception {
        CreatedTable table = createTable("O3", "열림없음");

        JsonNode response = performJson(get("/tables/{tableId}/sessions/current/orders", table.tableId()).with(staff()), 200);

        assertThat(response.path("session").isNull()).isTrue();
        assertThat(response.path("items")).isEmpty();
    }

    @Test
    @DisplayName("종료된 Session 목록을 최신순으로 조회하고 열린 Session은 제외한다")
    void 종료된_session_목록_조회_성공() throws Exception {
        CreatedTable table = createTable("O4", "과거");
        OpenedSession older = openAndCloseSession(table.tableId(), Instant.parse("2026-08-01T10:00:00Z"));
        OpenedSession newer = openAndCloseSession(table.tableId(), Instant.parse("2026-08-02T10:00:00Z"));
        openSession(table.tableId());

        JsonNode response =
                performJson(
                        get("/tables/{tableId}/sessions/history", table.tableId())
                                .with(staff())
                                .param("size", "10"),
                        200);

        assertThat(response.path("items")).hasSize(2);
        assertThat(response.path("items").get(0).path("sessionId").asText()).isEqualTo(newer.sessionId().toString());
        assertThat(response.path("items").get(1).path("sessionId").asText()).isEqualTo(older.sessionId().toString());
        assertThat(response.toString()).doesNotContain("\"status\":\"OPEN\"");
    }

    @Test
    @DisplayName("특정 과거 Session의 주문을 조회한다")
    void 과거_session_주문_조회_성공() throws Exception {
        CreatedTable table = createTable("O5", "과거주문");
        OpenedSession past = openAndCloseSession(table.tableId(), Instant.parse("2026-08-03T10:00:00Z"));
        orderReader.add(past.sessionId(), order("P-001", "COMPLETED", "SETTLED", "33000.00"));

        JsonNode response =
                performJson(
                        get("/tables/{tableId}/sessions/{sessionId}/orders", table.tableId(), past.sessionId())
                                .with(manager()),
                        200);

        assertThat(response.path("session").path("status").asText()).isEqualTo("CLOSED");
        assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("orderNumber").asText()).isEqualTo("P-001");
    }

    @Test
    @DisplayName("테이블과 Session 소속이 다르면 접근을 차단한다")
    void 테이블_session_소속_불일치_접근_차단() throws Exception {
        CreatedTable first = createTable("O6", "첫번째");
        CreatedTable second = createTable("O7", "두번째");
        OpenedSession past = openAndCloseSession(first.tableId(), Instant.parse("2026-08-03T11:00:00Z"));

        mockMvc
                .perform(get("/tables/{tableId}/sessions/{sessionId}/orders", second.tableId(), past.sessionId()).with(staff()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 업체 Table·Session 접근은 차단하고 내부 정보를 노출하지 않는다")
    void 다른_업체_접근_차단() throws Exception {
        OpenedSession session = openSession("O8", "업체");
        orderReader.add(session.sessionId(), order("TENANT-SECRET", "COMPLETED", "PAID", "1000.00"));

        MvcResult result =
                mockMvc
                        .perform(
                                get("/tables/{tableId}/sessions/current/orders", session.tableId())
                                        .with(identityActor("other-store", "STAFF")))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"))
                        .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("TENANT-SECRET", "업체");
    }

    @Test
    @DisplayName("관리자와 직원은 테이블 주문을 조회할 수 있다")
    void 관리자_직원_접근_성공() throws Exception {
        OpenedSession session = openSession("O9", "권한성공");

        mockMvc.perform(get("/tables/{tableId}/sessions/current/orders", session.tableId()).with(owner()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tables/{tableId}/sessions/current/orders", session.tableId()).with(staff()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("고객 권한은 테이블 주문 조회를 거부한다")
    void 권한_없는_사용자_거부() throws Exception {
        OpenedSession session = openSession("O10", "권한거부");

        mockMvc.perform(get("/tables/{tableId}/sessions/current/orders", session.tableId()).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cursor Pagination 첫 페이지와 다음 페이지를 조회한다")
    void cursor_pagination_첫페이지_다음페이지() throws Exception {
        OpenedSession session = openSession("O11", "페이지");
        orderReader.add(session.sessionId(), order("PAGE-1", "COMPLETED", "PAID", "1000.00"));
        orderReader.add(session.sessionId(), order("PAGE-2", "COMPLETED", "PAID", "2000.00"));
        orderReader.add(session.sessionId(), order("PAGE-3", "COMPLETED", "PAID", "3000.00"));

        JsonNode first =
                performJson(
                        get("/tables/{tableId}/sessions/current/orders", session.tableId())
                                .with(staff())
                                .param("size", "2"),
                        200);
        JsonNode second =
                performJson(
                        get("/tables/{tableId}/sessions/current/orders", session.tableId())
                                .with(staff())
                                .param("size", "2")
                                .param("cursor", first.path("nextCursor").asText()),
                        200);

        assertThat(first.path("items")).hasSize(2);
        assertThat(first.path("nextCursor").asText()).isNotBlank();
        assertThat(second.path("items")).hasSize(1);
        assertThat(second.path("nextCursor").isNull()).isTrue();
    }

    @Test
    @DisplayName("변조되거나 잘못된 Cursor는 거부한다")
    void 잘못된_cursor_거부() throws Exception {
        OpenedSession session = openSession("O12", "커서");
        orderReader.add(session.sessionId(), order("CURSOR-1", "COMPLETED", "PAID", "1000.00"));

        mockMvc
                .perform(
                        get("/tables/{tableId}/sessions/current/orders", session.tableId())
                                .with(staff())
                                .param("cursor", "tampered-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

        mockMvc
                .perform(
                        get("/tables/{tableId}/sessions/history", session.tableId())
                                .with(staff())
                                .param("cursor", "tampered-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    @DisplayName("기간 필터는 정상 동작하고 시작일이 종료일보다 늦으면 거부한다")
    void 기간_필터_정상과_잘못된_기간_검증() throws Exception {
        CreatedTable table = createTable("O13", "기간");
        OpenedSession excluded = openAndCloseSession(table.tableId(), Instant.parse("2026-08-01T10:00:00Z"));
        OpenedSession included = openAndCloseSession(table.tableId(), Instant.parse("2026-08-05T10:00:00Z"));

        JsonNode response =
                performJson(
                        get("/tables/{tableId}/sessions/history", table.tableId())
                                .with(staff())
                                .param("from", "2026-08-04T00:00:00Z")
                                .param("to", "2026-08-06T00:00:00Z"),
                        200);

        assertThat(response.path("items")).hasSize(1);
        assertThat(response.toString()).contains(included.sessionId().toString()).doesNotContain(excluded.sessionId().toString());

        mockMvc
                .perform(
                        get("/tables/{tableId}/sessions/history", table.tableId())
                                .with(staff())
                                .param("from", "2026-08-07T00:00:00Z")
                                .param("to", "2026-08-06T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    @DisplayName("주문 상태 필터는 정상 동작하고 지원하지 않는 상태는 거부한다")
    void 주문_상태_필터_정상과_미지원_검증() throws Exception {
        OpenedSession session = openSession("O14", "상태");
        orderReader.add(session.sessionId(), order("STATUS-1", "COMPLETED", "PAID", "1000.00"));
        orderReader.add(session.sessionId(), order("STATUS-2", "IN_PROGRESS", "UNPAID", "1000.00"));

        JsonNode response =
                performJson(
                        get("/tables/{tableId}/sessions/current/orders", session.tableId())
                                .with(staff())
                                .param("status", "completed"),
                        200);

        assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("status").asText()).isEqualTo("COMPLETED");

        mockMvc
                .perform(
                        get("/tables/{tableId}/sessions/current/orders", session.tableId())
                                .with(staff())
                                .param("status", "MADE_UP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_ORDER_STATUS"));
    }

    @Test
    @DisplayName("주문 금액과 Payment 의미 상태를 서버 Projection 그대로 반환한다")
    void 주문_금액과_payment_상태_반환() throws Exception {
        OpenedSession session = openSession("O15", "금액");
        orderReader.add(session.sessionId(), order("MONEY-1", "COMPLETED", "PARTIALLY_PAID", "19900.50"));

        JsonNode response =
                performJson(get("/tables/{tableId}/sessions/current/orders", session.tableId()).with(staff()), 200);

        assertThat(response.path("items").get(0).path("totalAmount").decimalValue()).isEqualByComparingTo("19900.50");
        assertThat(response.path("items").get(0).path("currency").asText()).isEqualTo("KRW");
        assertThat(response.path("items").get(0).path("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(response.path("items").get(0).path("items").get(0).path("productName").asText()).isEqualTo("아메리카노");
    }

    @Test
    @DisplayName("QR Token·digest·결제 민감정보를 Body와 Header에 노출하지 않는다")
    void 민감정보_비노출() throws Exception {
        OpenedSession session = openSession("O16", "민감");
        orderReader.add(session.sessionId(), order("SAFE-1", "COMPLETED", "PAID", "1000.00"));

        MvcResult result =
                mockMvc.perform(get("/tables/{tableId}/sessions/current/orders", session.tableId()).with(staff()))
                        .andExpect(status().isOk())
                        .andReturn();

        String headers = result.getResponse().getHeaderNames().toString();
        String body = result.getResponse().getContentAsString();
        assertThat(body + headers)
                .doesNotContain(
                        "token",
                        "digest",
                        "accessUrl",
                        "providerResponse",
                        "approvalKey",
                        "transactionKey",
                        "cardNumber");
    }

    @Test
    @DisplayName("현재 주문과 과거 주문이 서로 섞이지 않는다")
    void 현재_주문과_과거_주문_분리() throws Exception {
        CreatedTable table = createTable("O17", "분리");
        OpenedSession past = openAndCloseSession(table.tableId(), Instant.parse("2026-08-02T10:00:00Z"));
        OpenedSession current = openSession(table.tableId());
        orderReader.add(past.sessionId(), order("PAST-ONLY", "COMPLETED", "PAID", "1000.00"));
        orderReader.add(current.sessionId(), order("CURRENT-ONLY", "IN_PROGRESS", "UNPAID", "1000.00"));

        JsonNode currentResponse =
                performJson(get("/tables/{tableId}/sessions/current/orders", table.tableId()).with(staff()), 200);
        JsonNode pastResponse =
                performJson(get("/tables/{tableId}/sessions/{sessionId}/orders", table.tableId(), past.sessionId()).with(staff()), 200);

        assertThat(currentResponse.toString()).contains("CURRENT-ONLY").doesNotContain("PAST-ONLY");
        assertThat(pastResponse.toString()).contains("PAST-ONLY").doesNotContain("CURRENT-ONLY");
    }

    private OpenedSession openSession(String tableNumber, String displayName) throws Exception {
        return openSession(createTable(tableNumber, displayName).tableId());
    }

    private OpenedSession openSession(UUID tableId) throws Exception {
        JsonNode response =
                performJson(
                        post("/tables/{tableId}/sessions", tableId)
                                .with(staff())
                                .header("Idempotency-Key", UUID.randomUUID().toString()),
                        201);
        return new OpenedSession(tableId, UUID.fromString(response.path("sessionId").asText()));
    }

    private OpenedSession openAndCloseSession(UUID tableId, Instant closedAt) throws Exception {
        OpenedSession session = openSession(tableId);
        performJson(
                post("/tables/{tableId}/sessions/{sessionId}/close", tableId, session.sessionId())
                        .with(staff())
                        .header("Idempotency-Key", UUID.randomUUID().toString()),
                200);
        forceClosedAt(session.sessionId(), closedAt);
        return session;
    }

    private CreatedTable createTable(String tableNumber, String displayName) throws Exception {
        JsonNode body =
                performJson(
                        post("/tables")
                                .with(owner())
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", tableNumber,
                                                        "displayName", displayName,
                                                        "seatCapacity", 4))),
                        201);
        return new CreatedTable(UUID.fromString(body.path("tableId").asText()));
    }

    private JsonNode performJson(MockHttpServletRequestBuilder requestBuilder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(requestBuilder).andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void forceClosedAt(UUID sessionId, Instant closedAt) {
        jdbcClient.sql(
                        """
                        UPDATE table_usage_session
                           SET opened_at = :openedAt,
                               closed_at = :closedAt
                         WHERE session_id = :sessionId
                        """)
                .param("openedAt", closedAt.minusSeconds(3600))
                .param("closedAt", closedAt)
                .param("sessionId", TableIntegrationSupport.uuidBytes(sessionId))
                .update();
    }

    private RequestPostProcessor staff() {
        return user("staff").roles("STAFF");
    }

    private RequestPostProcessor manager() {
        return user("manager").roles("MANAGER");
    }

    private RequestPostProcessor owner() {
        return user("owner").roles("OWNER");
    }

    private RequestPostProcessor identityActor(String tenantId, String roleCode) {
        UUID accountId = UUID.randomUUID();
        IdentityPrincipal principal =
                new IdentityPrincipal(accountId, tenantId, roleCode, Set.of("table.order.read"), false);
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        principal, "N/A", List.of(new SimpleGrantedAuthority("table.order.read"))));
    }

    private static TableOrderSummaryResponse order(
            String orderNumber,
            String status,
            String paymentStatus,
            String totalAmount) {
        return new TableOrderSummaryResponse(
                UUID.randomUUID(),
                orderNumber,
                Instant.now(),
                status,
                new BigDecimal(totalAmount),
                "KRW",
                paymentStatus,
                List.of(
                        new TableOrderItemSummaryResponse(
                                UUID.randomUUID(),
                                "아메리카노",
                                1,
                                new BigDecimal(totalAmount))));
    }

    private record CreatedTable(UUID tableId) {}

    private record OpenedSession(UUID tableId, UUID sessionId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class OrderReaderTestConfiguration {

        @Bean
        @Primary
        MutableOrderReader mutableOrderReader() {
            return new MutableOrderReader();
        }
    }

    static class MutableOrderReader implements TableSessionOrderReader {

        private static final Set<String> SUPPORTED_STATUSES = Set.of("COMPLETED", "IN_PROGRESS", "CANCELLED");
        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

        private final Map<UUID, List<TableOrderSummaryResponse>> orders = new ConcurrentHashMap<>();

        @Override
        public OrderPage findOrders(OrderQuery query) {
            int offset = decodeCursor(query.cursor());
            List<TableOrderSummaryResponse> filtered =
                    new ArrayList<>(orders.getOrDefault(query.sessionId(), List.of())).stream()
                            .filter(order -> query.status() == null || order.status().equals(query.status()))
                            .sorted(
                                    Comparator.comparing(TableOrderSummaryResponse::createdAt)
                                            .reversed()
                                            .thenComparing(
                                                    order -> order.orderId().toString(),
                                                    Comparator.reverseOrder()))
                            .toList();
            int toIndex = Math.min(filtered.size(), offset + query.size());
            List<TableOrderSummaryResponse> items =
                    offset >= filtered.size() ? List.of() : filtered.subList(offset, toIndex);
            String nextCursor = toIndex < filtered.size() ? encodeCursor(toIndex) : null;
            return new OrderPage(items, nextCursor);
        }

        @Override
        public boolean supportsStatus(String status) {
            return SUPPORTED_STATUSES.contains(status);
        }

        void add(UUID sessionId, TableOrderSummaryResponse order) {
            orders.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(order);
        }

        void clear() {
            orders.clear();
        }

        private static int decodeCursor(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return 0;
            }
            try {
                String decoded = new String(DECODER.decode(cursor), StandardCharsets.US_ASCII);
                if (!decoded.startsWith("offset:")) {
                    throw invalidCursor();
                }
                int offset = Integer.parseInt(decoded.substring("offset:".length()));
                if (offset < 0) {
                    throw invalidCursor();
                }
                return offset;
            } catch (IllegalArgumentException exception) {
                throw invalidCursor();
            }
        }

        private static String encodeCursor(int offset) {
            return ENCODER.encodeToString(("offset:" + offset).getBytes(StandardCharsets.US_ASCII));
        }

        private static TableManagementException invalidCursor() {
            return new TableManagementException(
                    HttpStatus.BAD_REQUEST,
                    TableErrorCode.INVALID_CURSOR,
                    "Cursor is invalid.");
        }
    }
}
