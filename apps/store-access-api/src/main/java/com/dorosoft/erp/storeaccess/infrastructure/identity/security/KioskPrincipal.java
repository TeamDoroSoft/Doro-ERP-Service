package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The Cookie-authenticated Kiosk device (ADR-02-013): a Role-less {@code KIOSK_DEVICE} Principal, separate
 * from {@link EmployeePrincipal} and never backed by a Spring Session.
 */
public record KioskPrincipal(UUID tenantId, UUID storeId, UUID kioskDeviceId, String deviceCode) implements Serializable {

    public KioskPrincipal {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(kioskDeviceId, "kioskDeviceId must not be null");
        Objects.requireNonNull(deviceCode, "deviceCode must not be null");
    }
}
