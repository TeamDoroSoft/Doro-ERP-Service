package com.dorosoft.erp.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.testsupport.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
            "audit.security.payload-hmac.key-base64=AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM=",
            "table.security.idempotency.encryption-key-base64=BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc="
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("TABLE-02 테이블 관리 API 통합 테스트")
class TableManagementApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void 테이블을_비운다() {
        TableIntegrationSupport.cleanAuditTables(jdbcClient);
        TableIntegrationSupport.cleanTableTables(jdbcClient);
    }

    @Test
    @DisplayName("관리자는 테이블을 등록, 목록 조회, 상세 조회, 정보 변경, 비활성화, 활성화할 수 있다")
    void 정상_관리_흐름() throws Exception {
        CreatedTable created = createTable("A1", "창가", 4);

        mockMvc
                .perform(get("/tables").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableId").value(created.tableId().toString()))
                .andExpect(jsonPath("$[0].tableNumber").value("A1"))
                .andExpect(jsonPath("$[0].active").value(true));

        mockMvc
                .perform(get("/tables/{tableId}", created.tableId()).with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("창가"))
                .andExpect(jsonPath("$.seatCapacity").value(4));

        JsonNode updated =
                performJson(
                        put("/tables/{tableId}", created.tableId())
                                .with(user("manager").roles("MANAGER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("If-Match", created.version())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", "A2",
                                                        "displayName", "중앙",
                                                        "seatCapacity", 6))),
                        200);
        assertThat(updated.get("tableNumber").asText()).isEqualTo("A2");
        assertThat(updated.get("displayName").asText()).isEqualTo("중앙");
        assertThat(updated.get("seatCapacity").asInt()).isEqualTo(6);

        JsonNode deactivated =
                performJson(
                        patch("/tables/{tableId}/activation", created.tableId())
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("If-Match", updated.get("version").asLong())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(objectMapper.writeValueAsString(Map.of("active", false))),
                        200);
        assertThat(deactivated.get("active").asBoolean()).isFalse();

        mockMvc
                .perform(
                        patch("/tables/{tableId}/activation", created.tableId())
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("If-Match", deactivated.get("version").asLong())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(objectMapper.writeValueAsString(Map.of("active", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("테이블 번호는 정규화되어 중복 등록을 거부한다")
    void 정규화된_중복_테이블_번호를_거부한다() throws Exception {
        createTable("A1", "창가", 4);

        mockMvc
                .perform(
                        post("/tables")
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", " a1 ",
                                                        "displayName", "다른 자리",
                                                        "seatCapacity", 4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TABLE_NUMBER_DUPLICATED"));
    }

    @Test
    @DisplayName("좌석 수가 범위를 벗어나면 등록을 거부한다")
    void 잘못된_좌석_수를_거부한다() throws Exception {
        mockMvc
                .perform(
                        post("/tables")
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", "B1",
                                                        "displayName", "입구",
                                                        "seatCapacity", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리자 권한이 없으면 테이블 관리 요청을 거부한다")
    void 관리자_권한이_없으면_거부한다() throws Exception {
        mockMvc
                .perform(
                        post("/tables")
                                .with(user("staff").roles("STAFF"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", "C1",
                                                        "displayName", "홀",
                                                        "seatCapacity", 2))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("다른 업체 Principal은 테이블 관리 데이터에 접근하거나 변경할 수 없다")
    void 다른_업체_관리_접근_차단() throws Exception {
        CreatedTable table = createTable("TENANT-A", "업체 전용", 4);

        mockMvc
                .perform(get("/tables").with(identityManager("other-store")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        MvcResult detail =
                mockMvc
                        .perform(get("/tables/{tableId}", table.tableId()).with(identityManager("other-store")))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"))
                        .andReturn();
        assertThat(detail.getResponse().getContentAsString()).doesNotContain("TENANT-A", "업체 전용");

        MvcResult update =
                mockMvc
                        .perform(
                                put("/tables/{tableId}", table.tableId())
                                        .with(identityManager("other-store"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("If-Match", table.version())
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "tableNumber", "TENANT-B",
                                                                "displayName", "침범",
                                                                "seatCapacity", 6))))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"))
                        .andReturn();
        assertThat(update.getResponse().getContentAsString()).doesNotContain("TENANT-B", "침범");

        MvcResult activation =
                mockMvc
                        .perform(
                                patch("/tables/{tableId}/activation", table.tableId())
                                        .with(identityManager("other-store"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("If-Match", table.version())
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"))
                        .andReturn();
        assertThat(activation.getResponse().getContentAsString()).doesNotContain("TENANT-A", "업체 전용");

        mockMvc
                .perform(
                        post("/tables")
                                .with(identityManager("other-store"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", "TENANT-C",
                                                        "displayName", "다른 업체 생성",
                                                        "seatCapacity", 4))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"));

        assertThat(TableIntegrationSupport.countOf(jdbcClient, "store_table")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 요청은 등록을 반복하지 않고 저장된 응답을 재생한다")
    void 같은_요청_반복은_중복_등록하지_않는다() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body =
                objectMapper.writeValueAsString(
                        Map.of("tableNumber", "D1", "displayName", "반복", "seatCapacity", 4));

        JsonNode first =
                performJson(
                        post("/tables")
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", idempotencyKey)
                                .content(body),
                        201);
        JsonNode second =
                performJson(
                        post("/tables")
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", idempotencyKey)
                                .content(body),
                        201);

        assertThat(second.get("tableId").asText()).isEqualTo(first.get("tableId").asText());
        assertThat(TableIntegrationSupport.countOf(jdbcClient, "store_table")).isEqualTo(1);
    }

    @Test
    @DisplayName("낡은 If-Match version으로 변경하면 충돌로 거부한다")
    void 낡은_version_변경을_거부한다() throws Exception {
        CreatedTable created = createTable("E1", "처음", 4);
        String body =
                objectMapper.writeValueAsString(
                        Map.of("tableNumber", "E2", "displayName", "변경", "seatCapacity", 4));

        performJson(
                put("/tables/{tableId}", created.tableId())
                        .with(user("owner").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", created.version())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(body),
                200);

        mockMvc
                .perform(
                        put("/tables/{tableId}", created.tableId())
                                .with(user("owner").roles("OWNER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("If-Match", created.version())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "tableNumber", "E3",
                                                        "displayName", "충돌",
                                                        "seatCapacity", 4))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
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

    private RequestPostProcessor identityManager(String tenantId) {
        UUID accountId = UUID.randomUUID();
        IdentityPrincipal principal =
                new IdentityPrincipal(accountId, tenantId, "MANAGER", Set.of("table.manage"), false);
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        principal, "N/A", List.of(new SimpleGrantedAuthority("table.manage"))));
    }

    private record CreatedTable(UUID tableId, long version) {}
}
