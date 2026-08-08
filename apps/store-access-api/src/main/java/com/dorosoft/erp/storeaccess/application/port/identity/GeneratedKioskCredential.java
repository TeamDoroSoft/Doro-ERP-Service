package com.dorosoft.erp.storeaccess.application.port.identity;

/**
 * A freshly generated Kiosk credential (ADR-02-013). {@code fullCredential} ({@code kdc_<credentialId>.<secret>})
 * is returned to the caller exactly once and is never persisted; only a digest of {@code secret} is stored.
 */
public record GeneratedKioskCredential(String credentialId, String secret, String fullCredential) {
}
