package com.dorosoft.erp.storeaccess.presentation.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * End-to-end: Kiosk device register/Secret-rotate/revoke (ADR-02-013) through {@code /api/v1/kiosk-devices},
 * including the important-action reauthentication gate (ADR-02-011) and rejection of rotating a revoked
 * device.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "doro.identity.rate-limit.account.capacity=1000",
        "doro.identity.rate-limit.client-ip.capacity=1000"
})
@AutoConfigureMockMvc
@Testcontainers
class KioskDeviceControllerIntegrationTest {

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

    @Test
    void registerRotateAndRevokeSucceedAndRotatingARevokedDeviceFails() throws Exception {
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential").value(org.hamcrest.Matchers.startsWith("kdc_")));

        KioskDevice device = kioskDeviceRepository.findByTenantIdAndDeviceCode(tenant.tenantId(), deviceCode).orElseThrow();

        mockMvc.perform(post("/api/v1/kiosk-devices/" + device.id() + "/rotate")
                        .cookie(ownerSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kioskDeviceId").value(device.id().toString()));

        mockMvc.perform(post("/api/v1/kiosk-devices/" + device.id() + "/revoke")
                        .cookie(ownerSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/kiosk-devices/" + device.id() + "/rotate")
                        .cookie(ownerSession)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KIOSK_DEVICE_NOT_ACTIVE"));
    }

    @Test
    void registeringADeviceRequiresReauthenticationAfterFifteenMinutes() throws Exception {
        Tenant tenant = newTenant();
        LoginId ownerLoginId = newLoginId();
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), ownerLoginId, passwordEncoder.encode(PASSWORD),
                Role.OWNER, clock.instant()));
        Cookie ownerSession = loginAs(tenant.tenantCode(), ownerLoginId, PASSWORD);

        ((MutableClock) clock).advanceBy(Duration.ofMinutes(16));

        String deviceCode = "device-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mockMvc.perform(post("/api/v1/kiosk-devices")
                        .cookie(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"" + deviceCode + "\"}")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTHENTICATION_REQUIRED"));
    }

    @Test
    void staffCannotManageKioskDevices() throws Exception {
        Tenant tenant = newTenant();
        LoginId staffLoginId = newLoginId();
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenant.tenantId(), tenant.storeId(), staffLoginId, passwordEncoder.encode(PASSWORD),
                Role.STAFF, clock.instant()));
        Cookie staffSession = loginAs(tenant.tenantCode(), staffLoginId, PASSWORD);

        String deviceCode = "device-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mockMvc.perform(post("/api/v1/kiosk-devices")
                        .cookie(staffSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceCode\":\"" + deviceCode + "\"}")
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KIOSK_MANAGEMENT_FORBIDDEN"));
    }
}
