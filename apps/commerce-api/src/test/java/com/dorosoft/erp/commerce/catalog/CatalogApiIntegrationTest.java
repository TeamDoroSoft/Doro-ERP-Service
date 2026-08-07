package com.dorosoft.erp.commerce.catalog;

import static com.dorosoft.erp.commerce.support.CatalogTestFixtures.TENANT_A;
import static com.dorosoft.erp.commerce.support.CatalogTestFixtures.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.commerce.application.api.security.ActorRole;
import com.dorosoft.erp.commerce.infrastructure.security.ActorContextFilter;
import com.dorosoft.erp.commerce.support.CatalogTestFixtures;
import com.dorosoft.erp.commerce.support.CommerceIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * SOFT-433·SOFT-435: 공개 HTTP 계약, Role 차단과 오류 Code를 실제 Filter Chain으로 검증한다.
 */
@CommerceIntegrationTest
@AutoConfigureMockMvc
class CatalogApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        CatalogTestFixtures.clear();
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from product");
        jdbcTemplate.update("delete from menu_category");
    }

    @Test
    void unauthenticatedMenuRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/menu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void kioskDeviceCanReadMenuButCannotManageCatalog() throws Exception {
        UUID categoryId = createCategory("커피", 1);
        createProduct(categoryId, "아메리카노", 4500L);

        mockMvc.perform(actor(get("/api/v1/catalog/menu"), TENANT_A, ActorRole.KIOSK_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.categories[0].products[0].name").value("아메리카노"));

        mockMvc.perform(actor(post("/api/v1/catalog/categories"), TENANT_A, ActorRole.KIOSK_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"차\",\"displayOrder\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void kioskDeviceCannotChangeSoldOut() throws Exception {
        UUID categoryId = createCategory("커피", 1);
        UUID productId = createProduct(categoryId, "아메리카노", 4500L);

        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId + "/sold-out"),
                                TENANT_A, ActorRole.KIOSK_DEVICE)
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOut\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Boolean soldOut = jdbcTemplate.queryForObject(
                "select sold_out from product where id = ?", Boolean.class, productId);
        assertThat(soldOut).isFalse();
    }

    @Test
    void staffCanChangeSoldOut() throws Exception {
        UUID categoryId = createCategory("커피", 1);
        UUID productId = createProduct(categoryId, "아메리카노", 4500L);

        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId + "/sold-out"),
                                TENANT_A, ActorRole.STAFF)
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOut\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldOut").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void staffCannotEditProductBasics() throws Exception {
        UUID categoryId = createCategory("커피", 1);
        UUID productId = createProduct(categoryId, "아메리카노", 4500L);

        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId), TENANT_A, ActorRole.STAFF)
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void negativePriceIsRejectedWithValidationProblem() throws Exception {
        UUID categoryId = createCategory("커피", 1);

        mockMvc.perform(actor(post("/api/v1/catalog/products"), TENANT_A, ActorRole.OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId
                                + "\",\"name\":\"음수\",\"price\":-1,\"displayOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        Integer rows = jdbcTemplate.queryForObject("select count(*) from product", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void malformedPriceIsRejected() throws Exception {
        UUID categoryId = createCategory("커피", 1);

        mockMvc.perform(actor(post("/api/v1/catalog/products"), TENANT_A, ActorRole.OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId
                                + "\",\"name\":\"형식오류\",\"price\":\"비싼가격\",\"displayOrder\":1}"))
                .andExpect(status().isBadRequest());

        Integer rows = jdbcTemplate.queryForObject("select count(*) from product", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void missingIfMatchHeaderIsRejectedWithPreconditionRequired() throws Exception {
        UUID categoryId = createCategory("커피", 1);
        UUID productId = createProduct(categoryId, "아메리카노", 4500L);

        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId + "/sold-out"),
                                TENANT_A, ActorRole.MANAGER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOut\":true}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    }

    @Test
    void staleIfMatchHeaderIsRejectedWithConflict() throws Exception {
        UUID categoryId = createCategory("커피", 1);
        UUID productId = createProduct(categoryId, "아메리카노", 4500L);

        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId + "/sold-out"),
                                TENANT_A, ActorRole.MANAGER)
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOut\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId + "/sold-out"),
                                TENANT_A, ActorRole.MANAGER)
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOut\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_VERSION_CONFLICT"));
    }

    @Test
    void otherTenantProductIsNotFound() throws Exception {
        UUID categoryId = createCategory("커피", 1);
        UUID productId = createProduct(categoryId, "아메리카노", 4500L);

        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId + "/sold-out"),
                                TENANT_B, ActorRole.OWNER)
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOut\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        Boolean soldOut = jdbcTemplate.queryForObject(
                "select sold_out from product where id = ?", Boolean.class, productId);
        assertThat(soldOut).isFalse();
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + UUID.randomUUID() + "/sold-out"),
                                TENANT_A, ActorRole.MANAGER)
                        .header(HttpHeaders.IF_MATCH, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOut\":true}"))
                .andExpect(status().isNotFound());
    }

    private UUID createCategory(String name, int displayOrder) throws Exception {
        String body = mockMvc.perform(actor(post("/api/v1/catalog/categories"), TENANT_A, ActorRole.OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"displayOrder\":" + displayOrder + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(read(body, "categoryId"));
    }

    private UUID createProduct(UUID categoryId, String name, long price) throws Exception {
        String body = mockMvc.perform(actor(post("/api/v1/catalog/products"), TENANT_A, ActorRole.OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name
                                + "\",\"price\":" + price + ",\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(read(body, "productId"));
    }

    /** Jackson Version에 흔들리지 않도록 응답 본문은 JsonPath로 읽는다. */
    private static String read(String body, String field) {
        return JsonPath.read(body, "$." + field);
    }

    /** 통합 Test는 서명 검증을 끄고 Edge가 전달하는 Actor Context Header만 사용한다. */
    private static MockHttpServletRequestBuilder actor(
            MockHttpServletRequestBuilder builder, UUID tenantId, ActorRole role) {
        return builder
                .header(ActorContextFilter.HEADER_TENANT_ID, tenantId.toString())
                .header(ActorContextFilter.HEADER_STORE_ID, CatalogTestFixtures.STORE_A.toString())
                .header(ActorContextFilter.HEADER_ACTOR_TYPE, role.requiredActorType().name())
                .header(ActorContextFilter.HEADER_ACTOR_ID, UUID.randomUUID().toString())
                .header(ActorContextFilter.HEADER_ACTOR_ROLE, role.name());
    }
}
