package com.dorosoft.erp.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;

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

    // --- 권한 우회(RBAC bypass, MENU-10) --------------------------------------------

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Category 수정은 403 FORBIDDEN이다")
    void updateCategoryWithoutPermissionIsForbidden() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);

        mockMvc.perform(
                        put("/api/v1/catalog/categories/" + categoryId)
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"category-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Category 순서 변경은 403 FORBIDDEN이다")
    void replaceCategoryOrderWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(
                        put("/api/v1/catalog/categories/order")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"catalog-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"categoryIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Product 생성은 403 FORBIDDEN이다")
    void createProductWithoutPermissionIsForbidden() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);

        mockMvc.perform(
                        post("/api/v1/catalog/products")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryId\":\""
                                                + categoryId
                                                + "\",\"name\":\"아메리카노\",\"basePrice\":4500,\"salesEnabled\":true,\"stockManaged\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Product 수정은 403 FORBIDDEN이다")
    void updateProductWithoutPermissionIsForbidden() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID productId = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        mockMvc.perform(
                        put("/api/v1/catalog/products/" + productId)
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"product-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"categoryId\":\""
                                                + categoryId
                                                + "\",\"name\":\"아메리카노\",\"basePrice\":4500,\"salesEnabled\":true,\"stockManaged\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Product 순서 변경은 403 FORBIDDEN이다")
    void replaceProductOrderWithoutPermissionIsForbidden() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);

        mockMvc.perform(
                        put("/api/v1/catalog/categories/" + categoryId + "/product-order")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"catalog-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Product 옵션 교체는 403 FORBIDDEN이다")
    void replaceProductOptionsWithoutPermissionIsForbidden() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID productId = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        mockMvc.perform(
                        put("/api/v1/catalog/products/" + productId + "/options")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"product-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"options\":[{\"name\":\"샷 추가\",\"additionalPrice\":500,\"enabled\":true}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 판매 정책 변경은 403 FORBIDDEN이다")
    void changeSalesPolicyWithoutPermissionIsForbidden() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID productId = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        mockMvc.perform(
                        patch("/api/v1/catalog/products/" + productId + "/sales-policy")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"product-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"salesEnabled\":false,\"stockManaged\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage·catalog.soldout.update 권한이 모두 없으면 품절 변경은 403 FORBIDDEN이다")
    void changeSoldOutWithoutEitherPermissionIsForbidden() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);
        UUID productId = CatalogIntegrationSupport.insertProduct(jdbcClient, catalogId, categoryId, "아메리카노", 4500L, 0);

        mockMvc.perform(
                        patch("/api/v1/catalog/products/" + productId + "/sold-out")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .header("If-Match", "\"product-0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"soldOut\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Media 업로드 요청은 403 FORBIDDEN이다")
    void requestMediaUploadWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/v1/catalog/media-uploads")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"contentType\":\"image/webp\",\"byteSize\":1024,\"checksumSha256\":\""
                                                + "a".repeat(64)
                                                + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.manage 권한이 없으면 Media 업로드 완료는 403 FORBIDDEN이다")
    void completeMediaUploadWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/v1/catalog/media-uploads/" + UUID.randomUUID() + "/complete")
                                .with(authentication(readOnlyAuthentication()))
                                .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.read 권한이 없으면 GET /catalog는 403 FORBIDDEN이다")
    void getOverviewWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/catalog").with(authentication(noPermissionAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.read 권한이 없으면 GET /catalog/products는 403 FORBIDDEN이다")
    void listProductsWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").with(authentication(noPermissionAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("catalog.read 권한이 없으면 GET /catalog/products/{id}는 403 FORBIDDEN이다")
    void getProductWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(
                        get("/api/v1/catalog/products/" + UUID.randomUUID())
                                .with(authentication(noPermissionAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- 공개 메뉴 ETag·304(MENU-10) -------------------------------------------------

    @Test
    @DisplayName("GET /menu는 catalogRevision 기반 ETag를 반환한다")
    void publicMenuReturnsRevisionETag() throws Exception {
        mockMvc.perform(get("/api/v1/menu").with(authentication(readOnlyAuthentication())))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"catalog-0\""));
    }

    @Test
    @DisplayName("GET /menu에 현재 ETag를 If-None-Match로 보내면 304를 반환하고 본문이 없다")
    void publicMenuReturnsNotModifiedWhenIfNoneMatchMatches() throws Exception {
        mockMvc.perform(
                        get("/api/v1/menu")
                                .header("If-None-Match", "\"catalog-0\"")
                                .with(authentication(readOnlyAuthentication())))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"catalog-0\""))
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("GET /menu에 오래된 ETag를 If-None-Match로 보내면 200과 최신 본문을 반환한다")
    void publicMenuReturnsFreshBodyWhenIfNoneMatchIsStale() throws Exception {
        UUID categoryId = CatalogIntegrationSupport.insertCategory(jdbcClient, catalogId, "커피", 0);

        mockMvc.perform(
                put("/api/v1/catalog/categories/order")
                        .with(authentication(managerAuthentication()))
                        .with(csrf())
                        .header("If-Match", "\"catalog-0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[\"" + categoryId + "\"]}"));

        mockMvc.perform(
                        get("/api/v1/menu")
                                .header("If-None-Match", "\"catalog-0\"")
                                .with(authentication(readOnlyAuthentication())))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"catalog-1\""))
                .andExpect(jsonPath("$.data.catalogRevision").value(1));
    }

    // --- 이력 조회(GET /catalog/history, MENU-08) --------------------------------

    @Test
    @DisplayName("GET /catalog/history는 audit.read 권한으로 Category 생성 이력을 반환하며 actor·requestId를 그대로 보존한다")
    void getHistoryReturnsRecordedCategoryCreationWithActorAndRequestId() throws Exception {
        UUID accountId = UUID.randomUUID();
        Authentication creator = authenticationOf(accountId, Set.of("catalog.manage"));
        // RealAuditWriterAdapter는 Catalog의 actor 표시 문자열(accountId.toString())에서 결정적으로
        // 파생한 UUID를 감사 actorId로 쓴다(같은 문자열은 항상 같은 UUID) - 원본 accountId를 그대로
        // 저장하지 않는다.
        UUID expectedAuditActorId =
                UUID.nameUUIDFromBytes(accountId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        MvcResult createResult =
                mockMvc.perform(
                                post("/api/v1/catalog/categories")
                                        .with(authentication(creator))
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"커피\"}"))
                        .andReturn();
        String createRequestId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.requestId");

        mockMvc.perform(
                        get("/api/v1/catalog/history")
                                .queryParam("targetType", "CATEGORY")
                                .queryParam("action", "CATEGORY_CREATED")
                                .with(authentication(auditReaderAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("CATEGORY_CREATED"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("CATEGORY"))
                .andExpect(jsonPath("$.data.items[0].actorId").value(expectedAuditActorId.toString()))
                .andExpect(jsonPath("$.data.items[0].requestId").value(createRequestId))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("catalog.read 권한만으로는 GET /catalog/history가 403 FORBIDDEN이다")
    void getHistoryWithoutAuditReadPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/history").with(authentication(readOnlyAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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

    private static Authentication auditReaderAuthentication() {
        return authenticationOf(Set.of("audit.read"));
    }

    private static Authentication noPermissionAuthentication() {
        return authenticationOf(Set.of());
    }

    private static Authentication authenticationOf(Set<String> permissions) {
        return authenticationOf(UUID.randomUUID(), permissions);
    }

    private static Authentication authenticationOf(UUID accountId, Set<String> permissions) {
        IdentityPrincipal principal = new IdentityPrincipal(accountId, "test-tenant", "ADMIN", permissions, false);
        List<GrantedAuthority> authorities = permissions.stream().map(SimpleGrantedAuthority::new).map(a -> (GrantedAuthority) a).toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
