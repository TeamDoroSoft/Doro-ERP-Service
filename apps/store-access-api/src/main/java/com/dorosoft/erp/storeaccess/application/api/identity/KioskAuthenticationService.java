package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.api.identity.KioskCredentialFormat.ParsedKioskCredential;
import com.dorosoft.erp.storeaccess.application.port.identity.ActiveTenantContext;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskCredentialDigestSigner;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskDeviceRepository;
import com.dorosoft.erp.storeaccess.application.port.identity.TenantLookupPort;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDevice;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDeviceStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiosk activation and per-request Cookie authentication (ADR-02-013). {@link TenantLookupPort} follows the
 * same dormant-Port pattern as {@link EmployeeLoginService}: no production Adapter exists until feature 01 is
 * implemented, so it is resolved lazily and any actual activation/authentication attempt fails with
 * {@link AuthProblemCode#SESSION_VALIDATION_UNAVAILABLE} until it is.
 */
@Service
public class KioskAuthenticationService {

    private final KioskDeviceRepository kioskDeviceRepository;
    private final ObjectProvider<TenantLookupPort> tenantLookupPortProvider;
    private final KioskCredentialDigestSigner digestSigner;

    public KioskAuthenticationService(
            KioskDeviceRepository kioskDeviceRepository,
            ObjectProvider<TenantLookupPort> tenantLookupPortProvider,
            KioskCredentialDigestSigner digestSigner) {
        this.kioskDeviceRepository = kioskDeviceRepository;
        this.tenantLookupPortProvider = tenantLookupPortProvider;
        this.digestSigner = digestSigner;
    }

    @Transactional(readOnly = true)
    public KioskActivationResult activate(String tenantCode, String deviceCode, String secretRaw) {
        ActiveTenantContext tenant = resolveTenantByCode(tenantCode);
        if (tenant == null) {
            throw kioskAuthenticationFailed();
        }
        KioskDevice device = kioskDeviceRepository.findByTenantIdAndDeviceCode(tenant.tenantId(), deviceCode)
                .orElseThrow(this::kioskAuthenticationFailed);
        requireActiveWithMatchingSecret(device, secretRaw);

        return new KioskActivationResult(device.id(), KioskCredentialFormat.format(device.credentialId(), secretRaw));
    }

    @Transactional(readOnly = true)
    public KioskAuthenticationResult authenticateCookie(String cookieCredential) {
        ParsedKioskCredential parsed = KioskCredentialFormat.parse(cookieCredential).orElseThrow(this::kioskAuthenticationFailed);
        KioskDevice device = kioskDeviceRepository.findByCredentialId(parsed.credentialId())
                .orElseThrow(this::kioskAuthenticationFailed);
        requireActiveWithMatchingSecret(device, parsed.secret());

        ActiveTenantContext tenant = resolveTenantById(device.tenantId());
        if (tenant == null) {
            throw kioskAuthenticationFailed();
        }

        return new KioskAuthenticationResult(device.id(), device.tenantId(), device.storeId(), device.deviceCode());
    }

    private void requireActiveWithMatchingSecret(KioskDevice device, String secretRaw) {
        if (device.status() != KioskDeviceStatus.ACTIVE) {
            throw kioskAuthenticationFailed();
        }
        String computedDigest = digestSigner.digestSecret(secretRaw);
        if (!constantTimeEquals(computedDigest, device.secretDigest())) {
            throw kioskAuthenticationFailed();
        }
    }

    private ActiveTenantContext resolveTenantByCode(String tenantCode) {
        return requireTenantLookupPort().resolveActiveTenantByCode(tenantCode).orElse(null);
    }

    private ActiveTenantContext resolveTenantById(UUID tenantId) {
        return requireTenantLookupPort().resolveActiveTenantById(tenantId).orElse(null);
    }

    private TenantLookupPort requireTenantLookupPort() {
        TenantLookupPort tenantLookupPort = tenantLookupPortProvider.getIfAvailable();
        if (tenantLookupPort == null) {
            throw new ProblemAwareException(
                    AuthProblemCode.SESSION_VALIDATION_UNAVAILABLE, "Kiosk 인증을 처리할 수 없습니다.");
        }
        return tenantLookupPort;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private ProblemAwareException kioskAuthenticationFailed() {
        return new ProblemAwareException(AuthProblemCode.KIOSK_AUTHENTICATION_FAILED, "Kiosk 인증에 실패했습니다.");
    }
}
