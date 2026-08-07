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
 * SOFT-436·SOFT-438: POS와 Kiosk가 같은 판매 메뉴 계약을 받는지, 판매 불가 항목이 빠지는지 검증한다.
 */
@CommerceIntegrationTest
@AutoConfigureMockMvc
class CatalogChannelConsistencyIntegrationTest {

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
    void posAndKioskReceiveByteIdenticalSalesMenus() throws Exception {
        UUID coffee = createCategory("커피", 0);
        UUID dessert = createCategory("디저트", 1);
        createProduct(coffee, "아메리카노", 4500L, 0);
        createProduct(coffee, "카페라떼", 5000L, 1);
        createProduct(dessert, "치즈케이크", 6500L, 0);

        String owner = menuAs(ActorRole.OWNER);
        String manager = menuAs(ActorRole.MANAGER);
        String staff = menuAs(ActorRole.STAFF);
        String kiosk = menuAs(ActorRole.KIOSK_DEVICE);

        // 인증 주체와 Channel이 달라도 Catalog 결과는 완전히 같아야 한다.
        assertThat(staff).isEqualTo(kiosk);
        assertThat(owner).isEqualTo(kiosk);
        assertThat(manager).isEqualTo(kiosk);
        assertThat(JsonPath.<java.util.List<Object>>read(kiosk, "$.categories")).hasSize(2);
    }

    @Test
    void posAndKioskStayIdenticalAfterSoldOutAndDeactivation() throws Exception {
        UUID coffee = createCategory("커피", 0);
        UUID americano = createProduct(coffee, "아메리카노", 4500L, 0);
        UUID latte = createProduct(coffee, "카페라떼", 5000L, 1);

        soldOut(americano, 0L);
        deactivateProduct(latte, 0L);

        assertThat(menuAs(ActorRole.STAFF)).isEqualTo(menuAs(ActorRole.KIOSK_DEVICE));
        assertThat(JsonPath.<java.util.List<Object>>read(menuAs(ActorRole.KIOSK_DEVICE),
                "$.categories[0].products")).isEmpty();
    }

    @Test
    void theSalesMenuExcludesSoldOutInactiveAndOtherTenantProducts() throws Exception {
        UUID coffee = createCategory("커피", 0);
        UUID hidden = createCategory("숨김", 1);
        UUID sellable = createProduct(coffee, "아메리카노", 4500L, 0);
        UUID soldOutProduct = createProduct(coffee, "카페라떼", 5000L, 1);
        UUID inactiveProduct = createProduct(coffee, "콜드브루", 5500L, 2);
        UUID underInactiveCategory = createProduct(hidden, "비밀메뉴", 9000L, 0);

        soldOut(soldOutProduct, 0L);
        deactivateProduct(inactiveProduct, 0L);
        deactivateCategory(hidden, 0L);

        mockMvc.perform(actor(get("/api/v1/catalog/menu"), TENANT_A, ActorRole.KIOSK_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(1))
                .andExpect(jsonPath("$.categories[0].products.length()").value(1))
                .andExpect(jsonPath("$.categories[0].products[0].productId").value(sellable.toString()));

        // 다른 Tenant는 이 메뉴를 전혀 볼 수 없다.
        mockMvc.perform(actor(get("/api/v1/catalog/menu"), TENANT_B, ActorRole.KIOSK_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(0));

        // 제외된 항목은 삭제되지 않고 Row로 남는다.
        Integer rows = jdbcTemplate.queryForObject("select count(*) from product", Integer.class);
        assertThat(rows).isEqualTo(4);
        assertThat(underInactiveCategory).isNotNull();
    }

    @Test
    void theSalesMenuDoesNotExposeSellabilityFlagsBecauseEveryItemIsSellable() throws Exception {
        UUID coffee = createCategory("커피", 0);
        createProduct(coffee, "아메리카노", 4500L, 0);

        mockMvc.perform(actor(get("/api/v1/catalog/menu"), TENANT_A, ActorRole.KIOSK_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].products[0].price").value(4500))
                .andExpect(jsonPath("$.categories[0].products[0].soldOut").doesNotExist())
                .andExpect(jsonPath("$.categories[0].products[0].sellable").doesNotExist());
    }

    @Test
    void kioskDeviceCannotReadTheOperationsListButStaffCan() throws Exception {
        createCategory("커피", 0);

        mockMvc.perform(actor(get("/api/v1/catalog/products"), TENANT_A, ActorRole.STAFF))
                .andExpect(status().isOk());
        mockMvc.perform(actor(get("/api/v1/catalog/products"), TENANT_A, ActorRole.KIOSK_DEVICE))
                .andExpect(status().isForbidden());
    }

    private String menuAs(ActorRole role) throws Exception {
        return mockMvc.perform(actor(get("/api/v1/catalog/menu"), TENANT_A, role))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private UUID createCategory(String name, int displayOrder) throws Exception {
        String body = mockMvc.perform(actor(post("/api/v1/catalog/categories"), TENANT_A, ActorRole.OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"displayOrder\":" + displayOrder + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.categoryId"));
    }

    private UUID createProduct(UUID categoryId, String name, long price, int displayOrder) throws Exception {
        String body = mockMvc.perform(actor(post("/api/v1/catalog/products"), TENANT_A, ActorRole.OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"name\":\"" + name + "\",\"price\":"
                                + price + ",\"displayOrder\":" + displayOrder + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.productId"));
    }

    private void soldOut(UUID productId, long version) throws Exception {
        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId + "/sold-out"), TENANT_A,
                                ActorRole.STAFF)
                        .header(HttpHeaders.IF_MATCH, String.valueOf(version))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOut\":true}"))
                .andExpect(status().isOk());
    }

    private void deactivateProduct(UUID productId, long version) throws Exception {
        mockMvc.perform(actor(patch("/api/v1/catalog/products/" + productId), TENANT_A, ActorRole.MANAGER)
                        .header(HttpHeaders.IF_MATCH, String.valueOf(version))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
    }

    private void deactivateCategory(UUID categoryId, long version) throws Exception {
        mockMvc.perform(actor(patch("/api/v1/catalog/categories/" + categoryId), TENANT_A, ActorRole.MANAGER)
                        .header(HttpHeaders.IF_MATCH, String.valueOf(version))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());
    }

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
