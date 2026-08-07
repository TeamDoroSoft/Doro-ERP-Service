package com.dorosoft.erp.storeaccess.application.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskDeviceRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDevice;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDeviceStatus;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies Kiosk device registration/rotation/revocation: the OWNER/MANAGER authorization matrix (no
 * "MANAGER only STAFF" restriction, since Kiosk devices carry no Role), Tenant-scoped lookup, no grace
 * period on rotation, and that no plaintext secret is ever persisted (ADR-02-013).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
                + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration"
})
@Testcontainers
class KioskManagementServiceIntegrationTest {

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
    private KioskManagementService service;

    @Autowired
    private KioskDeviceRepository kioskDeviceRepository;

    @Autowired
    private com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository employeeAccountRepository;

    @Autowired
    private Clock clock;

    @Test
    void ownerCanRegisterDevice() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSaveEmployee(tenantId, storeId, Role.OWNER));

        KioskCredentialIssuance issuance = service.registerDevice(owner, "POS-" + UUID.randomUUID());

        assertThat(issuance.credential()).startsWith("kdc_");
        KioskDevice saved = kioskDeviceRepository.findById(issuance.kioskDeviceId()).orElseThrow();
        assertThat(saved.status()).isEqualTo(KioskDeviceStatus.ACTIVE);
        assertThat(saved.credentialVersion()).isEqualTo(1);
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.storeId()).isEqualTo(storeId);
    }

    @Test
    void managerCanRegisterDevice() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext manager = actorFor(createAndSaveEmployee(tenantId, storeId, Role.MANAGER));

        KioskCredentialIssuance issuance = service.registerDevice(manager, "POS-" + UUID.randomUUID());

        assertThat(kioskDeviceRepository.findById(issuance.kioskDeviceId())).isPresent();
    }

    @Test
    void staffCannotRegisterRotateOrRevokeDevices() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext staff = actorFor(createAndSaveEmployee(tenantId, storeId, Role.STAFF));
        ActorContext owner = actorFor(createAndSaveEmployee(tenantId, storeId, Role.OWNER));
        KioskDevice existing = registerRawDevice(owner, "POS-" + UUID.randomUUID());

        assertThatThrownBy(() -> service.registerDevice(staff, "POS-" + UUID.randomUUID()))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(KioskManagementProblemCode.KIOSK_MANAGEMENT_FORBIDDEN);
        assertThatThrownBy(() -> service.rotateCredential(staff, existing.id()))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(KioskManagementProblemCode.KIOSK_MANAGEMENT_FORBIDDEN);
        assertThatThrownBy(() -> service.revokeDevice(staff, existing.id()))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(KioskManagementProblemCode.KIOSK_MANAGEMENT_FORBIDDEN);
    }

    @Test
    void rotatingReplacesTheCredentialImmediatelyWithNoGracePeriod() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSaveEmployee(tenantId, storeId, Role.OWNER));
        KioskCredentialIssuance registered = service.registerDevice(owner, "POS-" + UUID.randomUUID());
        KioskDevice beforeRotation = kioskDeviceRepository.findById(registered.kioskDeviceId()).orElseThrow();
        String oldCredentialId = beforeRotation.credentialId();

        KioskCredentialIssuance rotated = service.rotateCredential(owner, registered.kioskDeviceId());

        assertThat(rotated.credential()).isNotEqualTo(registered.credential());
        KioskDevice afterRotation = kioskDeviceRepository.findById(registered.kioskDeviceId()).orElseThrow();
        assertThat(afterRotation.credentialVersion()).isEqualTo(2);
        assertThat(afterRotation.credentialId()).isNotEqualTo(oldCredentialId);
        assertThat(kioskDeviceRepository.findByCredentialId(oldCredentialId)).isEmpty();
        assertThat(kioskDeviceRepository.findByCredentialId(afterRotation.credentialId())).isPresent();
    }

    @Test
    void rotatingARevokedDeviceIsRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSaveEmployee(tenantId, storeId, Role.OWNER));
        KioskCredentialIssuance registered = service.registerDevice(owner, "POS-" + UUID.randomUUID());
        service.revokeDevice(owner, registered.kioskDeviceId());

        assertThatThrownBy(() -> service.rotateCredential(owner, registered.kioskDeviceId()))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(KioskManagementProblemCode.KIOSK_DEVICE_NOT_ACTIVE);
    }

    @Test
    void ownerCanRevokeDeviceAndExistingOrdersAreImpliedPreservedByNotDeletingTheRow() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSaveEmployee(tenantId, storeId, Role.OWNER));
        KioskCredentialIssuance registered = service.registerDevice(owner, "POS-" + UUID.randomUUID());

        service.revokeDevice(owner, registered.kioskDeviceId());

        KioskDevice revoked = kioskDeviceRepository.findById(registered.kioskDeviceId()).orElseThrow();
        assertThat(revoked.status()).isEqualTo(KioskDeviceStatus.REVOKED);
        assertThat(revoked.credentialVersion()).isEqualTo(2);
    }

    @Test
    void targetInAnotherTenantIsTreatedAsNotFound() {
        ActorContext ownerInTenantA = actorFor(
                createAndSaveEmployee(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER));
        ActorContext ownerInTenantB = actorFor(
                createAndSaveEmployee(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER));
        KioskDevice deviceInTenantB = registerRawDevice(ownerInTenantB, "POS-" + UUID.randomUUID());

        assertThatThrownBy(() -> service.rotateCredential(ownerInTenantA, deviceInTenantB.id()))
                .asInstanceOf(type(ProblemAwareException.class))
                .extracting(ProblemAwareException::code)
                .isEqualTo(KioskManagementProblemCode.KIOSK_DEVICE_NOT_FOUND);
    }

    @Test
    void duplicateDeviceCodeWithinTheSameTenantAndStoreIsRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSaveEmployee(tenantId, storeId, Role.OWNER));
        String deviceCode = "POS-" + UUID.randomUUID();
        service.registerDevice(owner, deviceCode);

        assertThatThrownBy(() -> service.registerDevice(owner, deviceCode))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theStoredDigestIsNeverThePlaintextSecretAndDiffersAcrossDevices() {
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        ActorContext owner = actorFor(createAndSaveEmployee(tenantId, storeId, Role.OWNER));

        KioskCredentialIssuance first = service.registerDevice(owner, "POS-" + UUID.randomUUID());
        KioskCredentialIssuance second = service.registerDevice(owner, "POS-" + UUID.randomUUID());

        KioskDevice firstDevice = kioskDeviceRepository.findById(first.kioskDeviceId()).orElseThrow();
        KioskDevice secondDevice = kioskDeviceRepository.findById(second.kioskDeviceId()).orElseThrow();
        assertThat(first.credential()).doesNotContain(firstDevice.secretDigest());
        assertThat(firstDevice.secretDigest()).isNotEqualTo(secondDevice.secretDigest());
    }

    private KioskDevice registerRawDevice(ActorContext actor, String deviceCode) {
        KioskCredentialIssuance issuance = service.registerDevice(actor, deviceCode);
        return kioskDeviceRepository.findById(issuance.kioskDeviceId()).orElseThrow();
    }

    private EmployeeAccount createAndSaveEmployee(UUID tenantId, UUID storeId, Role role) {
        EmployeeAccount account = EmployeeAccount.createWithTemporaryPassword(
                UUID.randomUUID(), tenantId, storeId,
                LoginId.normalize("user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)),
                "argon2-hash", role, clock.instant());
        return employeeAccountRepository.save(account);
    }

    private ActorContext actorFor(EmployeeAccount account) {
        return new ActorContext(account.tenantId(), account.storeId(), account.id(), account.role());
    }
}
