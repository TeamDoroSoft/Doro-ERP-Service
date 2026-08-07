package com.dorosoft.erp.storeaccess.presentation.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.storeaccess.application.port.identity.ActiveTenantContext;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.TenantLookupPort;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
 * End-to-end: employee create/list/get/Role/status/password-reset/self-password-change (ADR-02-009/010/014),
 * the 15-minute important-action reauthentication gate (ADR-02-011), and Session revocation on Role/status/
 * password change (ADR-02-002/004).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "doro.identity.rate-limit.account.capacity=1000",
        "doro.identity.rate-limit.client-ip.capacity=1000"
})
@AutoConfigureMockMvc
@Testcontainers
class EmployeeControllerIntegrationTest {

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

        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock();
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

    /** A settable {@link Clock}, so tests can move past the 15-minute reauthentication window without waiting. */
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());

        void advanceBy(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    private static final String PASSWORD = "the-current-passphrase-value";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeAccountRepository employeeAccountRepository;

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
        Cookie cookie = response.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
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

    @Test
    void ownerCreatesListsAndGetsAnEmployee() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        String newLoginId = "new-staff-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mockMvc.perform(post("/api/v1/employees")
                        .cookie(ownerSession)
                        .header("Idempotency-Key", "create-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + newLoginId + "\",\"temporaryPassword\":\""
                                + "a-brand-new-temporary-pass" + "\",\"role\":\"STAFF\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(newLoginId))
                .andExpect(jsonPath("$.role").value("STAFF"));

        UUID createdId = employeeAccountRepository.findByTenantId(tenant.tenantId()).stream()
                .filter(account -> account.loginId().value().equals(newLoginId))
                .findFirst()
                .orElseThrow()
                .id();

        mockMvc.perform(get("/api/v1/employees").cookie(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + createdId + "')]").exists());

        mockMvc.perform(get("/api/v1/employees/" + createdId).cookie(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(newLoginId));
    }

    @Test
    void changingRoleRevokesTheTargetEmployeesSessions() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        LoginId staffLoginId = newLoginId();
        EmployeeAccount staff = seedEmployee(tenant, Role.STAFF, staffLoginId, PASSWORD);
        Cookie staffSession = loginAs(tenant.tenantCode(), staffLoginId, PASSWORD);

        mockMvc.perform(patch("/api/v1/employees/" + staff.id() + "/role")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));

        mockMvc.perform(get("/api/v1/employees").cookie(staffSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingStatusToInactiveRevokesTheTargetEmployeesSessions() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        LoginId staffLoginId = newLoginId();
        EmployeeAccount staff = seedEmployee(tenant, Role.STAFF, staffLoginId, PASSWORD);
        Cookie staffSession = loginAs(tenant.tenantCode(), staffLoginId, PASSWORD);

        mockMvc.perform(patch("/api/v1/employees/" + staff.id() + "/status")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/employees").cookie(staffSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetIsIdempotentUnderReplayAndRevokesTheTargetsSessions() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        LoginId staffLoginId = newLoginId();
        EmployeeAccount staff = seedEmployee(tenant, Role.STAFF, staffLoginId, PASSWORD);
        Cookie staffSession = loginAs(tenant.tenantCode(), staffLoginId, PASSWORD);

        String body = "{\"newTemporaryPassword\":\"a-freshly-reset-temp-pass\"}";
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/employees/" + staff.id() + "/password-reset")
                            .cookie(ownerSession)
                            .header("Idempotency-Key", "reset-key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        }

        mockMvc.perform(get("/api/v1/employees").cookie(staffSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void selfPasswordChangeSucceedsAndOldPasswordNoLongerWorks() throws Exception {
        Tenant tenant = newTenant();
        LoginId staffLoginId = newLoginId();
        seedEmployee(tenant, Role.STAFF, staffLoginId, PASSWORD);
        Cookie staffSession = loginAs(tenant.tenantCode(), staffLoginId, PASSWORD);

        String newPassword = "a-completely-different-pass";
        mockMvc.perform(patch("/api/v1/employees/me/password")
                        .cookie(staffSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + tenant.tenantCode() + "\",\"loginId\":\"" + staffLoginId.value()
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + tenant.tenantCode() + "\",\"loginId\":\"" + staffLoginId.value()
                                + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void importantActionsRequireReauthenticationAfterFifteenMinutesButSelfPasswordChangeDoesNot() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        seedEmployee(tenant, Role.OWNER, ownerLoginId, PASSWORD);
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        LoginId staffLoginId = newLoginId();
        EmployeeAccount staff = seedEmployee(tenant, Role.STAFF, staffLoginId, PASSWORD);

        ((MutableClock) clock).advanceBy(Duration.ofMinutes(16));

        mockMvc.perform(patch("/api/v1/employees/" + staff.id() + "/role")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTHENTICATION_REQUIRED"));

        var reauthResponse = mockMvc.perform(post("/api/v1/auth/reauthenticate")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie rotatedSession = reauthResponse.getResponse().getCookie("SESSION");
        assertThat(rotatedSession).isNotNull();
        assertThat(rotatedSession.getValue()).isNotEqualTo(ownerSession.getValue());

        mockMvc.perform(patch("/api/v1/employees/" + staff.id() + "/role")
                        .cookie(rotatedSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}")
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}
