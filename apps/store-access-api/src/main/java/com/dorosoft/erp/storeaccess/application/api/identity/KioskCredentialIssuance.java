package com.dorosoft.erp.storeaccess.application.api.identity;

import java.util.UUID;

/**
 * Result of registering a Kiosk device or rotating its credential: the one-time plaintext credential the
 * caller must display to the operator and never fetch again (ADR-02-013).
 */
public record KioskCredentialIssuance(UUID kioskDeviceId, String credential) {
}
