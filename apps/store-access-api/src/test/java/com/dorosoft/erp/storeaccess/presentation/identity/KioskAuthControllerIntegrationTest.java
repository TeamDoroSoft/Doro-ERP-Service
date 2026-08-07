package com.dorosoft.erp.storeaccess.presentation.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.storeaccess.application.api.identity.KioskCredentialFormat;
import com.dorosoft.erp.storeaccess.application.port.identity.ActiveTenantContext;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskDeviceRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.TenantLookupPort;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDevice;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
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
 * End-to-end: Kiosk activation and Cookie authentication (ADR-02-013) — Cookie issuance, rejection of a wrong
 * Secret, rejection of a revoked device's Cookie, the 401 AMBIGUOUS_AUTHENTICATION rule when a valid employee
 * Session and a valid Kiosk Cookie are both presented, and the 7-day Cookie-refresh threshold.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "doro.identity.rate-limit.account.capacity=1000",
        "doro.identity.rate-limit.client-ip.capacity=1000"
})
@AutoConfigureMockMvc
@Testcontainers
class KioskAuthControllerIntegrationTest {

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
    private static final String KIOSK_COOKIE_NAME = "DORO_KIOSK_DEVICE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeAccountRepository employeeAccountRepository;

    @Autowired
    private KioskDeviceRepository kioskDeviceRepository;

    @Autowired
    private InMemoryTenantLookupPort tenantLookupPort;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

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

    private Cookie loginAs(String tenantCode, LoginId loginId, String password) throws Exception {
        var response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + tenantCode + "\",\"loginId\":\"" + loginId.value()
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return response.getResponse().getCookie("SESSION");
    }

    private Cookie registerDeviceAndActivate(Tenant tenant, Cookie ownerSession, String deviceCode) throws Exception {
        var registerResponse = mockMvc.perform(post("/api/v1/kiosk-devices")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"" + deviceCode + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String credential = extractField(registerResponse.getResponse().getContentAsString(), "credential");
        String secret = KioskCredentialFormat.parse(credential).orElseThrow().secret();

        var activateResponse = mockMvc.perform(post("/api/v1/kiosk-auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + tenant.tenantCode() + "\",\"deviceCode\":\"" + deviceCode
                                + "\",\"secret\":\"" + secret + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie kioskCookie = activateResponse.getResponse().getCookie(KIOSK_COOKIE_NAME);
        assertThat(kioskCookie).isNotNull();
        assertThat(kioskCookie.isHttpOnly()).isTrue();
        assertThat(kioskCookie.getSecure()).isTrue();
        assertThat(kioskCookie.getPath()).isEqualTo("/api/v1");
        return kioskCookie;
    }

    private static String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    @Test
    void activationWithWrongSecretFailsAndDoesNotSetACookie() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), ownerLoginId, passwordEncoder.encode(PASSWORD),
                Role.OWNER, clock.instant()));
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        String deviceCode = "device-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mockMvc.perform(post("/api/v1/kiosk-devices")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"" + deviceCode + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        var activateResponse = mockMvc.perform(post("/api/v1/kiosk-auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantCode\":\"" + tenant.tenantCode() + "\",\"deviceCode\":\"" + deviceCode
                                + "\",\"secret\":\"wrong-secret-value\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("KIOSK_AUTHENTICATION_FAILED"))
                .andReturn();
        assertThat(activateResponse.getResponse().getCookie(KIOSK_COOKIE_NAME)).isNull();
    }

    @Test
    void ambiguousAuthenticationIsRejectedWhenBothEmployeeSessionAndKioskCookieAreValid() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), ownerLoginId, passwordEncoder.encode(PASSWORD),
                Role.OWNER, clock.instant()));
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        String deviceCode = "device-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Cookie kioskCookie = registerDeviceAndActivate(tenant, ownerSession, deviceCode);

        mockMvc.perform(get("/api/v1/employees").cookie(ownerSession, kioskCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_AUTHENTICATION"));
    }

    @Test
    void aStaleKioskCookieAlongsideAValidEmployeeSessionIsIgnoredNotRejected() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), ownerLoginId, passwordEncoder.encode(PASSWORD),
                Role.OWNER, clock.instant()));
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);
        Cookie garbageKioskCookie = new Cookie(KIOSK_COOKIE_NAME, "kdc_not-a-real-credential.garbage");

        mockMvc.perform(get("/api/v1/employees").cookie(ownerSession, garbageKioskCookie))
                .andExpect(status().isOk());
    }

    @Test
    void revokedDevicesCookieIsRejectedAndExpired() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), ownerLoginId, passwordEncoder.encode(PASSWORD),
                Role.OWNER, clock.instant()));
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        String deviceCode = "device-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Cookie kioskCookie = registerDeviceAndActivate(tenant, ownerSession, deviceCode);

        KioskDevice device = kioskDeviceRepository.findByTenantIdAndDeviceCode(tenant.tenantId(), deviceCode).orElseThrow();
        mockMvc.perform(post("/api/v1/kiosk-devices/" + device.id() + "/revoke")
                        .cookie(ownerSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        var response = mockMvc.perform(get("/api/v1/employees").cookie(kioskCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("KIOSK_AUTHENTICATION_FAILED"))
                .andReturn();
        Cookie expiredCookie = response.getResponse().getCookie(KIOSK_COOKIE_NAME);
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getMaxAge()).isEqualTo(0);
    }

    @Test
    void freshlyActivatedCookieIsNotReissuedButAnAgingOneIsRefreshed() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), ownerLoginId, passwordEncoder.encode(PASSWORD),
                Role.OWNER, clock.instant()));
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        String deviceCode = "device-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Cookie kioskCookie = registerDeviceAndActivate(tenant, ownerSession, deviceCode);

        var freshResponse = mockMvc.perform(get("/api/v1/employees").cookie(kioskCookie))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(freshResponse.getResponse().getCookie(KIOSK_COOKIE_NAME)).isNull();

        KioskDevice device = kioskDeviceRepository.findByTenantIdAndDeviceCode(tenant.tenantId(), deviceCode).orElseThrow();
        kioskDeviceRepository.save(device.recordAuthentication(clock.instant().minus(Duration.ofDays(24))));

        var agingResponse = mockMvc.perform(get("/api/v1/employees").cookie(kioskCookie))
                .andExpect(status().isUnauthorized())
                .andReturn();
        Cookie refreshedCookie = agingResponse.getResponse().getCookie(KIOSK_COOKIE_NAME);
        assertThat(refreshedCookie).isNotNull();
        assertThat(refreshedCookie.getMaxAge()).isEqualTo((int) Duration.ofDays(30).toSeconds());
    }
}
