package com.dorosoft.erp.storeaccess.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.IdempotencyRecordRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskDeviceRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecord;
import com.dorosoft.erp.storeaccess.domain.identity.IdempotencyRecordStatus;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDevice;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryEventType;
import com.dorosoft.erp.storeaccess.domain.identity.SecurityHistoryResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies the identity Flyway migration applies cleanly, Hibernate validates against it, and each
 * repository adapter round-trips through the real PostgreSQL unique constraints (ADR-02-008, ADR-02-013,
 * ADR-02-014).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
@Testcontainers
class IdentityPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void identitySecrets(DynamicPropertyRegistry registry) {
        registry.add("doro.identity.hmac.rate-limit-secret", () -> "test-rate-limit-secret");
        registry.add("doro.identity.hmac.idempotency-secret", () -> "test-idempotency-secret");
        registry.add("doro.identity.hmac.kiosk-credential-secret", () -> "test-kiosk-credential-secret");
    }

    @Autowired
    private EmployeeAccountRepository employeeAccountRepository;

    @Autowired
    private KioskDeviceRepository kioskDeviceRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private EmployeeSecurityHistoryRepository securityHistoryRepository;

    @Autowired
    private Clock clock;

    @Test
    void contextLoadsWithFlywayMigratedSchemaAndHibernateValidation() {
        assertThat(clock).isNotNull();
    }

    @Test
    @Transactional
    void savesAndReloadsEmployeeAccountByTenantAndLoginId() {
        UUID tenantId = UUID.randomUUID();
        LoginId loginId = LoginId.normalize("Owner01");
        EmployeeAccount account = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), loginId, "argon2-hash", Role.OWNER, clock.instant());

        EmployeeAccount saved = employeeAccountRepository.save(account);

        assertThat(employeeAccountRepository.findById(saved.id())).contains(saved);
        assertThat(employeeAccountRepository.findByTenantIdAndLoginId(tenantId, loginId)).contains(saved);
    }

    @Test
    @Transactional
    void rejectsDuplicateLoginIdWithinTheSameTenant() {
        UUID tenantId = UUID.randomUUID();
        LoginId loginId = LoginId.normalize("owner02");
        employeeAccountRepository.save(EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), loginId, "argon2-hash-1", Role.OWNER, clock.instant()));

        EmployeeAccount duplicate = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), loginId, "argon2-hash-2", Role.MANAGER, clock.instant());

        assertThatThrownBy(() -> employeeAccountRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void savesAndReloadsKioskDeviceByCredentialId() {
        String credentialId = "cred-" + UUID.randomUUID();
        KioskDevice device = KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POS-01", credentialId, "digest",
                clock.instant());

        KioskDevice saved = kioskDeviceRepository.save(device);

        assertThat(kioskDeviceRepository.findById(saved.id())).contains(saved);
        assertThat(kioskDeviceRepository.findByCredentialId(credentialId)).contains(saved);
    }

    @Test
    @Transactional
    void rejectsDuplicateCredentialId() {
        String credentialId = "cred-" + UUID.randomUUID();
        kioskDeviceRepository.save(KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POS-01", credentialId, "digest",
                clock.instant()));

        KioskDevice duplicate = KioskDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "POS-02", credentialId, "digest-2",
                clock.instant());

        assertThatThrownBy(() -> kioskDeviceRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void claimsAndCompletesIdempotencyRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = clock.instant();
        IdempotencyRecord claimed = IdempotencyRecord.claim(
                UUID.randomUUID(), tenantId, actorId, "CREATE_EMPLOYEE", "key-digest", "request-hmac",
                now, now.plus(Duration.ofHours(24)));

        IdempotencyRecord saved = idempotencyRecordRepository.save(claimed);

        assertThat(idempotencyRecordRepository.findByScope(tenantId, actorId, "CREATE_EMPLOYEE", "key-digest"))
                .contains(saved);

        IdempotencyRecord completed = idempotencyRecordRepository.save(
                saved.complete("{\"employeeId\":\"abc\"}", now.plusSeconds(1)));

        assertThat(completed.status()).isEqualTo(IdempotencyRecordStatus.COMPLETED);
    }

    @Test
    @Transactional
    void rejectsDuplicateIdempotencyScope() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = clock.instant();
        idempotencyRecordRepository.save(IdempotencyRecord.claim(
                UUID.randomUUID(), tenantId, actorId, "CREATE_EMPLOYEE", "same-key-digest", "request-hmac-1",
                now, now.plus(Duration.ofHours(24))));

        IdempotencyRecord duplicate = IdempotencyRecord.claim(
                UUID.randomUUID(), tenantId, actorId, "CREATE_EMPLOYEE", "same-key-digest", "request-hmac-2",
                now, now.plus(Duration.ofHours(24)));

        assertThatThrownBy(() -> idempotencyRecordRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void savesAndReloadsSecurityHistory() {
        EmployeeSecurityHistory history = EmployeeSecurityHistory.occur(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SecurityHistoryEventType.EMPLOYEE_LOGIN_FAILED, null, null, null,
                SecurityHistoryResult.FAILURE, "INVALID_CREDENTIALS", null, null, clock.instant());

        EmployeeSecurityHistory saved = securityHistoryRepository.save(history);

        assertThat(securityHistoryRepository.findById(saved.id())).contains(saved);
    }
}
