package com.dorosoft.erp.storeaccess.application.port.identity;

/**
 * Computes the non-reversible digest stored for a Kiosk device's secret (ADR-02-013). The raw secret is
 * never stored.
 */
public interface KioskCredentialDigestSigner {

    String digestSecret(String rawSecret);
}
