package com.dorosoft.erp.storeaccess.application.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.ActiveTenantContext;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.SecurityHistoryQuery;
import com.dorosoft.erp.storeaccess.application.port.identity.TenantLookupPort;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies employee login (ADR-02-002/003/006/008/009) against a Test-source Stub {@link TenantLookupPort} —
 * feature 01 owns the real Adapter and has not been implemented yet (CLAUDE_기능02_구현_가이드.md §6).
 */
@SpringBootTest(properties = {
        "doro.identity.rate-limit.account.capacity=1000",
        "doro.identity.rate-limit.client-ip.capacity=1000"
})
@Testcontainers
class EmployeeLoginServiceIntegrationTest {

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
    static class StubTenantLookupConfig {

        @Bean
        TenantLookupPort tenantLookupPort() {
            return new InMemoryTenantLookupPort();
        }
    }

    /** Test-only Stub (CLAUDE_기능02_구현_가이드.md §6 explicitly allows this in Test source only). */
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
    private EmployeeLoginService service;

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

    @Test
    void succeedsWithCorrectCredentialsAndResetsFailureState() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = registerTenant(tenantId, storeId);
        EmployeeAccount account = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE, PASSWORD);

        LoginResult result = service.login(
                new LoginCommand(tenantCode, account.loginId().value(), PASSWORD, "203.0.113.1"));

        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.storeId()).isEqualTo(storeId);
        assertThat(result.employeeId()).isEqualTo(account.id());
        assertThat(result.role()).isEqualTo(Role.STAFF);
        assertThat(result.absoluteExpiresAt()).isEqualTo(result.authenticatedAt().plus(Duration.ofHours(12)));
        EmployeeAccount reloaded = employeeAccountRepository.findById(account.id()).orElseThrow();
        assertThat(reloaded.failedLoginCount()).isZero();
    }

    @Test
    void wrongPasswordFailsAndRecordsHistory() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = registerTenant(tenantId, storeId);
        EmployeeAccount account = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE, PASSWORD);

        assertThatThrownBy(() -> service.login(
                new LoginCommand(tenantCode, account.loginId().value(), "the-wrong-passphrase-value", "203.0.113.1")))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(AuthProblemCode.AUTHENTICATION_FAILED);

        EmployeeAccount reloaded = employeeAccountRepository.findById(account.id()).orElseThrow();
        assertThat(reloaded.failedLoginCount()).isEqualTo(1);
        assertThat(historyFor(tenantId, SecurityHistoryEventType.EMPLOYEE_LOGIN_FAILED, account.id())).hasSize(1);
    }

    @Test
    void fiveConsecutiveFailuresLockTheAccountAndRecordBothEvents() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = registerTenant(tenantId, storeId);
        EmployeeAccount account = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE, PASSWORD);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(
                    new LoginCommand(tenantCode, account.loginId().value(), "the-wrong-passphrase-value", "203.0.113.1")));
        }

        EmployeeAccount reloaded = employeeAccountRepository.findById(account.id()).orElseThrow();
        assertThat(reloaded.isLocked(clock.instant())).isTrue();
        assertThat(historyFor(tenantId, SecurityHistoryEventType.EMPLOYEE_LOGIN_FAILED, account.id())).hasSize(5);
        assertThat(historyFor(tenantId, SecurityHistoryEventType.EMPLOYEE_ACCOUNT_LOCKED, account.id())).hasSize(1);
    }

    @Test
    void aLockedAccountRejectsEvenTheCorrectPasswordWithoutChangingLockoutState() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = registerTenant(tenantId, storeId);
        EmployeeAccount account = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE, PASSWORD);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(
                    new LoginCommand(tenantCode, account.loginId().value(), "the-wrong-passphrase-value", "203.0.113.1")));
        }
        EmployeeAccount lockedAccount = employeeAccountRepository.findById(account.id()).orElseThrow();

        assertThatThrownBy(() -> service.login(new LoginCommand(tenantCode, account.loginId().value(), PASSWORD, "203.0.113.1")))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(AuthProblemCode.AUTHENTICATION_FAILED);

        EmployeeAccount reloaded = employeeAccountRepository.findById(account.id()).orElseThrow();
        assertThat(reloaded.lockedUntil()).isEqualTo(lockedAccount.lockedUntil());
        assertThat(historyFor(tenantId, SecurityHistoryEventType.EMPLOYEE_LOGIN_FAILED, account.id())).hasSize(5);
    }

    @Test
    void unknownTenantCodeFailsUniformly() {
        assertThatThrownBy(() -> service.login(
                new LoginCommand("no-such-tenant", "someone", PASSWORD, "203.0.113.1")))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(AuthProblemCode.AUTHENTICATION_FAILED);
    }

    @Test
    void unknownLoginIdFailsUniformly() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = registerTenant(tenantId, storeId);

        assertThatThrownBy(() -> service.login(
                new LoginCommand(tenantCode, "nonexistent-user", PASSWORD, "203.0.113.1")))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(AuthProblemCode.AUTHENTICATION_FAILED);
    }

    @Test
    void inactiveEmployeeWithCorrectPasswordFailsWithoutChangingLockoutState() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        String tenantCode = registerTenant(tenantId, storeId);
        EmployeeAccount account = createAndSave(tenantId, storeId, Role.STAFF, EmployeeStatus.ACTIVE, PASSWORD);
        employeeAccountRepository.save(account.changeStatus(EmployeeStatus.INACTIVE, clock.instant()));

        assertThatThrownBy(() -> service.login(
                new LoginCommand(tenantCode, account.loginId().value(), PASSWORD, "203.0.113.1")))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(AuthProblemCode.AUTHENTICATION_FAILED);

        EmployeeAccount reloaded = employeeAccountRepository.findById(account.id()).orElseThrow();
        assertThat(reloaded.failedLoginCount()).isZero();
    }

    private String registerTenant(UUID tenantId, UUID storeId) {
        String tenantCode = "tenant-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        tenantLookupPort.register(tenantCode, tenantId, storeId);
        return tenantCode;
    }

    private List<com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory> historyFor(
            UUID tenantId, SecurityHistoryEventType eventType, UUID targetId) {
        Instant now = clock.instant();
        SecurityHistoryQuery query = new SecurityHistoryQuery(
                tenantId, now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)), eventType, null, targetId,
                null, null, null, 100, null);
        return employeeSecurityHistoryRepository.search(query);
    }

    private EmployeeAccount createAndSave(
            UUID tenantId, UUID storeId, Role role, EmployeeStatus status, String rawPassword) {
        EmployeeAccount account = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, storeId, freshLoginId(), passwordEncoder.encode(rawPassword), role,
                clock.instant());
        if (status == EmployeeStatus.INACTIVE) {
            account = account.changeStatus(EmployeeStatus.INACTIVE, clock.instant());
        }
        return employeeAccountRepository.save(account);
    }

    private LoginId freshLoginId() {
        return LoginId.normalize("user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
    }
}
