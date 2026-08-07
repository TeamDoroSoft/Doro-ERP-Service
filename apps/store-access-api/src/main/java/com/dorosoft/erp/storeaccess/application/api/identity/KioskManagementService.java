package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.port.identity.GeneratedKioskCredential;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskCredentialDigestSigner;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskCredentialGenerator;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskDeviceRepository;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDevice;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDeviceStatus;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiosk device registration, Secret rotation and revocation (ADR-02-013). OWNER and MANAGER may both manage
 * Kiosk devices — unlike employee management, there is no "MANAGER may only touch STAFF" restriction, since
 * a Kiosk device carries no Role. {@link ActorContext} stands in for the authenticated Session until
 * feature 02 step 2's login is complete; a future Controller will build it from the Session instead.
 *
 * <p>Registration and rotation are intentionally not idempotency-wrapped: ADR-02-013 explicitly excludes
 * applying the employee creation/password-reset idempotency contract here.
 */
@Service
public class KioskManagementService {

    private final KioskDeviceRepository kioskDeviceRepository;
    private final KioskCredentialGenerator credentialGenerator;
    private final KioskCredentialDigestSigner digestSigner;
    private final SecurityHistoryRecorder securityHistoryRecorder;
    private final Clock clock;

    public KioskManagementService(
            KioskDeviceRepository kioskDeviceRepository,
            KioskCredentialGenerator credentialGenerator,
            KioskCredentialDigestSigner digestSigner,
            SecurityHistoryRecorder securityHistoryRecorder,
            Clock clock) {
        this.kioskDeviceRepository = kioskDeviceRepository;
        this.credentialGenerator = credentialGenerator;
        this.digestSigner = digestSigner;
        this.securityHistoryRecorder = securityHistoryRecorder;
        this.clock = clock;
    }

    @Transactional
    public KioskCredentialIssuance registerDevice(ActorContext actor, String deviceCode) {
        requireCanManageKiosk(actor);
        GeneratedKioskCredential generated = credentialGenerator.generate();
        KioskDevice device = KioskDevice.register(
                UUID.randomUUID(), actor.tenantId(), actor.storeId(), deviceCode, generated.credentialId(),
                digestSigner.digestSecret(generated.secret()), clock.instant());
        KioskDevice saved = kioskDeviceRepository.save(device);
        securityHistoryRecorder.recordKioskDeviceRegistered(
                actor.tenantId(), actor.storeId(), actor.employeeId(), saved.id());
        return new KioskCredentialIssuance(saved.id(), generated.fullCredential());
    }

    @Transactional
    public KioskCredentialIssuance rotateCredential(ActorContext actor, UUID kioskDeviceId) {
        requireCanManageKiosk(actor);
        KioskDevice device = requireManageableDevice(actor, kioskDeviceId);
        if (device.status() != KioskDeviceStatus.ACTIVE) {
            throw new ProblemAwareException(
                    KioskManagementProblemCode.KIOSK_DEVICE_NOT_ACTIVE,
                    "폐기된 Kiosk 기기는 Secret을 회전할 수 없습니다.");
        }
        GeneratedKioskCredential generated = credentialGenerator.generate();
        KioskDevice rotated = device.rotate(
                generated.credentialId(), digestSigner.digestSecret(generated.secret()), clock.instant());
        KioskDevice saved = kioskDeviceRepository.save(rotated);
        securityHistoryRecorder.recordKioskCredentialRotated(
                actor.tenantId(), actor.storeId(), actor.employeeId(), saved.id(),
                device.credentialVersion(), saved.credentialVersion());
        return new KioskCredentialIssuance(saved.id(), generated.fullCredential());
    }

    @Transactional
    public void revokeDevice(ActorContext actor, UUID kioskDeviceId) {
        requireCanManageKiosk(actor);
        KioskDevice device = requireManageableDevice(actor, kioskDeviceId);
        kioskDeviceRepository.save(device.revoke(clock.instant()));
        securityHistoryRecorder.recordKioskDeviceRevoked(
                actor.tenantId(), actor.storeId(), actor.employeeId(), device.id());
    }

    private KioskDevice requireManageableDevice(ActorContext actor, UUID kioskDeviceId) {
        return kioskDeviceRepository.findById(kioskDeviceId)
                .filter(device -> device.tenantId().equals(actor.tenantId()))
                .orElseThrow(() -> new ProblemAwareException(
                        KioskManagementProblemCode.KIOSK_DEVICE_NOT_FOUND, "Kiosk 기기를 찾을 수 없습니다."));
    }

    private void requireCanManageKiosk(ActorContext actor) {
        if (actor.role() == Role.STAFF) {
            throw new ProblemAwareException(
                    KioskManagementProblemCode.KIOSK_MANAGEMENT_FORBIDDEN, "Kiosk 기기 관리 권한이 없습니다.");
        }
    }
}
