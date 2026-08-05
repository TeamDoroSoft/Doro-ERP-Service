package com.dorosoft.erp.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Catalog HTTP API 통합 테스트. 실제 MySQL·실제 Service Chain을 그대로 태우고, Spring Security
 * Method Security({@code @PreAuthorize})만 {@code CatalogTestApplication}의 {@code @EnableMethodSecurity}로
 * 활성화한다(apps/erp-api의 HttpSecurity Filter Chain 전체는 이 모듈 테스트 범위 밖).
 */
@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@AutoConfigureMockMvc
@Import(com.dorosoft.erp.testsupport.MySqlTestcontainersConfiguration.class)
@DisplayName("Catalog HTTP API 통합 테스트 - 실제 MySQL로 오류 계약·권한·응답 형태를 검증한다")
class CatalogControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;

    private UUID catalogId;

    @BeforeEach
    void 테이블을_비우고_Catalog를_초기화한다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
    }

    // --- 생성·응답 형태 ----------------------------------------------------------

    @Test
    @DisplayName("Category 생성은 201과 Location, ETag, data.category.name, requestId를 반환한다")
    void createCategoryReturnsCreatedEnvelope() throws Exception {
        mockMvc.perform(
                        post("/api/v1/catalog/categories")
                                .with(authentication(managerAuthentication()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"커피\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/catalog/categories/")))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.data.category.name").value("커피"))
                .andExpect(jsonPath("$.data.category.displayOrder").value(0))
                .andExpect(jsonPath("$.data.catalogRevision").value(1))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Category 생성은 403 FORBIDDEN이다")
    void createCategoryWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/v1/catalog/categories")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"커피\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("이름이 비어 있으면 Category 생성은 400 VALIDATION_FAILED다")
    void createCategoryWithBlankNameIsValidationFailed() throws Exception {
        mockMvc.perform(
                        post("/api/v1/catalog/categories")
                                .with(authentication(managerAuthentication()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다른 내용을 재요청하면 409 IDEMPOTENCY_KEY_REUSED다")
    void createCategoryWithReusedIdempotencyKeyIsConflict() throws Exception {
        mockMvc.perform(
                post("/api/v1/catalog/categories")
                        .with(authentication(managerAuthentication()))
                                .with(csrf())
                        .header("Idempotency-Key", "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"커피\"}"));

        mockMvc.perform(
                        post("/api/v1/catalog/categories")
                                .with(authentication(managerAuthentication()))
                                .with(csrf())
                                .header("Idempotency-Key", "same-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"차\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    // --- 404 -------------------------------------------------------------------

    @Test
    @DisplayName("존재하지 않는 Category 수정은 404 CATEGORY_NOT_FOUND다")
    void updateNonExistentCategoryIsNotFound() throws Exception {
        mockMvc.perform(
                        put("/api/v1/catalog/categories/" + UUID.randomUUID())
                                .with(authentication(managerAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"category-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    // --- 409 VERSION_CONFLICT (Problem Details status/code/requestId) ----------

    @Test
    @DisplayName("오래된 If-Match로 Category를 수정하면 409 VERSION_CONFLICT이고 status·code·requestId를 모두 포함한다")
    void updateCategoryWithStaleIfMatchIsVersionConflict() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);

        mockMvc.perform(
                        put("/api/v1/catalog/categories/" + categoryId)
                                .with(authentication(managerAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"category-99\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    // --- Product 생성 ------------------------------------------------------------

    @Test
    @DisplayName("Product 생성은 201과 함께 기본 정보를 그대로 반환한다")
    void createProductReturnsCreatedEnvelope() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);

        mockMvc.perform(
                        post("/api/v1/catalog/products")
                                .with(authentication(managerAuthentication()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryId\":\""
                                                + categoryId
                                                + "\",\"name\":\"아메리카노\",\"description\":\"진한 에스프레소\","
                                                + "\"basePrice\":4500,\"salesEnabled\":true,\"stockManaged\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.product.name").value("아메리카노"))
                .andExpect(jsonPath("$.data.product.basePrice").value(4500))
                .andExpect(jsonPath("$.data.product.soldOut").value(false));
    }

    @Test
    @DisplayName("수동 품절 변경은 catalog.soldout.update 권한만으로 충분하다")
    void changeSoldOutWithSoldOutPermissionSucceeds() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID productId = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        mockMvc.perform(
                        patch("/api/v1/catalog/products/" + productId + "/sold-out")
                                .with(authentication(soldOutOnlyAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"product-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"soldOut\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.soldOut").value(true));
    }

    // --- 조회 --------------------------------------------------------------------

    @Test
    @DisplayName("GET /catalog는 전체 Category·Product와 catalogRevision을 반환한다")
    void getOverviewReturnsCategoriesAndProducts() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        mockMvc.perform(get("/api/v1/catalog").with(authentication(readOnlyAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].name").value("커피"))
                .andExpect(jsonPath("$.data.categories[0].products[0].name").value("아메리카노"));
    }

    /**
     * "인증 없이 접근 가능"이라는 SecurityFilterChain의 permitAll 규칙은 IdentitySecurityConfiguration
     * (apps/erp-api 전체 조립)에만 있고 이 모듈 테스트 Context는 가져오지 않는다(클래스 상단 문서 참고).
     * 여기서는 이 Endpoint 자체의 응답 로직(판매 활성 필터·순서·필드)만 검증한다.
     */
    @Test
    @DisplayName("GET /menu는 판매 활성 상품만 Category·Product·Option 순서로 반환한다")
    void publicMenuReturnsOnlySalesEnabledProducts() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        mockMvc.perform(get("/api/v1/menu").with(authentication(readOnlyAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].products[0].name").value("아메리카노"))
                .andExpect(jsonPath("$.data.categories[0].products[0].orderable").value(true));
    }

    private static Authentication managerAuthentication() {
        return authenticationOf(Set.of("catalog.manage"));
    }

    private static Authentication readOnlyAuthentication() {
        return authenticationOf(Set.of("catalog.read"));
    }

    private static Authentication soldOutOnlyAuthentication() {
        return authenticationOf(Set.of("catalog.soldout.update"));
    }

    private static Authentication authenticationOf(Set<String> permissions) {
        IdentityPrincipal principal = new IdentityPrincipal(UUID.randomUUID(), "test-tenant", "ADMIN", permissions, false);
        List<GrantedAuthority> authorities = permissions.stream().map(SimpleGrantedAuthority::new).map(a -> (GrantedAuthority) a).toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
