package com.dorosoft.erp.storeaccess.presentation.identity;

import com.dorosoft.erp.storeaccess.application.api.identity.KioskCredentialIssuance;
import com.dorosoft.erp.storeaccess.application.api.identity.KioskManagementService;
import com.dorosoft.erp.storeaccess.application.api.identity.UserActivity;
import com.dorosoft.erp.storeaccess.application.port.identity.CurrentActorResolver;
import com.dorosoft.erp.storeaccess.application.port.identity.RecentReauthenticationChecker;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiosk device register/Secret-rotate/revoke (ADR-02-013). All three are important management actions:
 * genuine user activity (ADR-02-003's idle-timeout renewal list explicitly includes "계정·Kiosk 기기 관리") and
 * require a recent password reauthentication (ADR-02-011's important-action list explicitly includes
 * "Kiosk 등록·Secret 회전·폐기"). Neither registration nor rotation is idempotency-wrapped (ADR-02-013
 * explicitly excludes applying the employee creation/password-reset idempotency contract here).
 */
@RestController
@RequestMapping("/api/v1/kiosk-devices")
class KioskDeviceController {

    private final KioskManagementService kioskManagementService;
    private final CurrentActorResolver currentActorResolver;
    private final RecentReauthenticationChecker recentReauthenticationChecker;

    KioskDeviceController(
            KioskManagementService kioskManagementService,
            CurrentActorResolver currentActorResolver,
            RecentReauthenticationChecker recentReauthenticationChecker) {
        this.kioskManagementService = kioskManagementService;
        this.currentActorResolver = currentActorResolver;
        this.recentReauthenticationChecker = recentReauthenticationChecker;
    }

    @UserActivity
    @PostMapping
    ResponseEntity<KioskCredentialResponse> registerDevice(@Valid @RequestBody RegisterKioskDeviceRequest request) {
        recentReauthenticationChecker.requireRecentReauthentication();
        KioskCredentialIssuance issued = kioskManagementService.registerDevice(
                currentActorResolver.currentActor(), request.deviceCode());
        return ResponseEntity.ok(toResponse(issued));
    }

    @UserActivity
    @PostMapping("/{kioskDeviceId}/rotate")
    ResponseEntity<KioskCredentialResponse> rotateCredential(@PathVariable UUID kioskDeviceId) {
        recentReauthenticationChecker.requireRecentReauthentication();
        KioskCredentialIssuance issued = kioskManagementService.rotateCredential(
                currentActorResolver.currentActor(), kioskDeviceId);
        return ResponseEntity.ok(toResponse(issued));
    }

    @UserActivity
    @PostMapping("/{kioskDeviceId}/revoke")
    ResponseEntity<Void> revokeDevice(@PathVariable UUID kioskDeviceId) {
        recentReauthenticationChecker.requireRecentReauthentication();
        kioskManagementService.revokeDevice(currentActorResolver.currentActor(), kioskDeviceId);
        return ResponseEntity.noContent().build();
    }

    private KioskCredentialResponse toResponse(KioskCredentialIssuance issuance) {
        return new KioskCredentialResponse(issuance.kioskDeviceId(), issuance.credential());
    }
}
