package com.dorosoft.erp.storeaccess.presentation.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.storeaccess.application.port.identity.ActiveTenantContext;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.TenantLookupPort;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.UserActivity;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** End-to-end: login establishes a real Session an authenticated request can then use; logout ends it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "doro.identity.rate-limit.account.capacity=1000",
        "doro.identity.rate-limit.client-ip.capacity=1000"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

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

        @RestController
        static class WhoAmITestController {

            @UserActivity
            @GetMapping("/api/v1/_test/whoami")
            ResponseEntity<Void> whoami() {
                return ResponseEntity.ok().build();
            }
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

    @Test
    void loginEstablishesASessionThatAuthenticatesFollowUpRequestsUntilLogout() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = "tenant-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        tenantLookupPort.register(tenantCode, tenantId, storeId);
        LoginId loginId = LoginId.normalize("user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        EmployeeAccount account = employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, storeId, loginId, passwordEncoder.encode(PASSWORD),
                com.dorosoft.erp.storeaccess.domain.identity.Role.OWNER, clock.instant()));

        var loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + tenantCode + "\",\"loginId\":\"" + loginId.value()
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(account.id().toString()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andReturn();
        Cookie sessionCookie = loginResponse.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.getSecure()).isTrue();
        assertThat(sessionCookie.isHttpOnly()).isTrue();

        mockMvc.perform(get("/api/v1/_test/whoami").cookie(sessionCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/_test/whoami").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordReturnsAuthenticationFailedProblem() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = "tenant-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        tenantLookupPort.register(tenantCode, tenantId, storeId);
        LoginId loginId = LoginId.normalize("user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, storeId, loginId, passwordEncoder.encode(PASSWORD),
                com.dorosoft.erp.storeaccess.domain.identity.Role.STAFF, clock.instant()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + tenantCode + "\",\"loginId\":\"" + loginId.value()
                                + "\",\"password\":\"wrong-password-value\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }
}
