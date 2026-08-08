package com.dorosoft.erp.storeaccess.presentation.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.storeaccess.application.port.identity.ActiveTenantContext;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.TenantLookupPort;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryResult;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end: {@code GET /api/v1/security-history} (ADR-02-015) — STAFF is forbidden, and an OWNER can see the
 * local security history event a preceding employee-management action just recorded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "doro.identity.rate-limit.account.capacity=1000",
        "doro.identity.rate-limit.client-ip.capacity=1000"
})
@AutoConfigureMockMvc
@Testcontainers
class SecurityHistoryControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("doro.identity.hmac.rate-limit-secret", () -> "test-rate-limit-secret");
        registry.add("doro.identity.hmac.idempotency-secret", () -> "test-idempotency-secret");
        registry.add("doro.identity.hmac.kiosk-credential-secret", () -> "test-kiosk-credential-secret");
    }

    @TestConfiguration
    static class TestSupportConfig {

        @Bean
        TenantLookupPort tenantLookupPort() {
            return new InMemoryTenantLookupPort();
        }
    }

    static class InMemoryTenantLookupPort implements TenantLookupPort {

        private final Map<String, ActiveTenantContext> tenants = new ConcurrentHashMap<>();

        void register(String tenantCode, UUID tenantId, UUID storeId) {
            tenants.put(tenantCode, new ActiveTenantContext(tenantId, storeId));
        }

        @Override
        public Optional<ActiveTenantContext> resolveActiveTenantByCode(String tenantCode) {
            return Optional.ofNullable(tenants.get(tenantCode));
        }

        @Override
        public Optional<ActiveTenantContext> resolveActiveTenantById(UUID tenantId) {
            return tenants.values().stream().filter(tenant -> tenant.tenantId().equals(tenantId)).findFirst();
        }
    }

    private static final String PASSWORD = "the-current-passphrase-value";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeAccountRepository employeeAccountRepository;

    @Autowired
    private EmployeeSecurityHistoryRepository employeeSecurityHistoryRepository;

    @Autowired
    private InMemoryTenantLookupPort tenantLookupPort;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private Cookie loginAs(String tenantCode, LoginId loginId, String password) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + tenantCode + "\",\"loginId\":\"" + loginId.value()
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return response.getResponse().getCookie("SESSION");
    }

    private record Tenant(String tenantCode, UUID tenantId, UUID storeId) {
    }

    private Tenant newTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = "tenant-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        tenantLookupPort.register(tenantCode, tenantId, storeId);
        return new Tenant(tenantCode, tenantId, storeId);
    }

    private LoginId newLoginId() {
        return LoginId.normalize("user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }

    private EmployeeAccount seedEmployee(Tenant tenant, Role role, LoginId loginId, String password) {
        return employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), loginId, passwordEncoder.encode(password),
                role, clock.instant()));
    }

    private void seedHistory(Tenant tenant, UUID targetId, Instant occurredAt) {
        employeeSecurityHistoryRepository.save(EmployeeSecurityHistory.occur(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), SecurityHistoryEventType.EMPLOYEE_CREATED,
                UUID.randomUUID(), "EMPLOYEE", targetId, SecurityHistoryResult.SUCCESS, null, null, null,
                occurredAt));
    }

    @Test
    void ownerSeesTheRoleChangeEventItJustRecorded() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        LoginId staffLoginId = newLoginId();
        EmployeeAccount staff = seedEmployee(tenant, Role.STAFF, staffLoginId, PASSWORD);

        mockMvc.perform(patch("/api/v1/employees/" + staff.id() + "/role")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        String from = clock.instant().minus(Duration.ofHours(1)).toString();
        String to = clock.instant().plus(Duration.ofHours(1)).toString();
        mockMvc.perform(get("/api/v1/security-history")
                        .cookie(ownerSession)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("eventType", "EMPLOYEE_ROLE_CHANGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventType").value("EMPLOYEE_ROLE_CHANGED"))
                .andExpect(jsonPath("$.items[0].targetId").value(staff.id().toString()));
    }

    @Test
    void staffIsForbiddenFromQueryingSecurityHistory() throws Exception {
        Tenant tenant = newTenant();
        LoginId staffLoginId = newLoginId();
        seedEmployee(tenant, Role.STAFF, staffLoginId, PASSWORD);
        Cookie staffSession = loginAs(tenant.tenantCode(), staffLoginId, PASSWORD);

        String from = clock.instant().minus(Duration.ofHours(1)).toString();
        String to = clock.instant().plus(Duration.ofHours(1)).toString();
        mockMvc.perform(get("/api/v1/security-history")
                        .cookie(staffSession)
                        .queryParam("from", from)
                        .queryParam("to", to))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECURITY_HISTORY_ACCESS_FORBIDDEN"));
    }

    @Test
    void rangeLongerThanNinetyDaysIsRejected() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        String from = clock.instant().minus(Duration.ofDays(100)).toString();
        String to = clock.instant().toString();
        mockMvc.perform(get("/api/v1/security-history")
                        .cookie(ownerSession)
                        .queryParam("from", from)
                        .queryParam("to", to))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SECURITY_HISTORY_RANGE"));
    }

    @Test
    void supplyingOnlyOneCursorParameterIsRejected() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        String from = clock.instant().minus(Duration.ofHours(1)).toString();
        String to = clock.instant().plus(Duration.ofHours(1)).toString();
        mockMvc.perform(get("/api/v1/security-history")
                        .cookie(ownerSession)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("cursorOccurredAt", clock.instant().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SECURITY_HISTORY_CURSOR"));

        mockMvc.perform(get("/api/v1/security-history")
                        .cookie(ownerSession)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("cursorId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SECURITY_HISTORY_CURSOR"));
    }

    @Test
    void hasMoreReflectsWhetherARecordExistsBeyondTheRequestedPage() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        Instant base = clock.instant();
        for (int i = 0; i < 3; i++) {
            seedHistory(tenant, UUID.randomUUID(), base.minusSeconds(i));
        }
        String from = base.minus(Duration.ofHours(1)).toString();
        String to = base.plus(Duration.ofHours(1)).toString();

        mockMvc.perform(get("/api/v1/security-history")
                        .cookie(ownerSession)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(get("/api/v1/security-history")
                        .cookie(ownerSession)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true));
    }
}
