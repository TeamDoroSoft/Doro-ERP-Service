package com.dorosoft.erp.storeaccess.application.api.identity;

import java.util.UUID;

/**
 * A successfully Cookie-authenticated Kiosk device (ADR-02-013), for the infrastructure Filter to build the
 * {@code KIOSK_DEVICE} Principal.
 */
public record KioskAuthenticationResult(UUID kioskDeviceId, UUID tenantId, UUID storeId, String deviceCode) {
}
