package com.dorosoft.erp.storeaccess.application.port.identity;

/** Generates a random Kiosk device credential (ADR-02-013). */
public interface KioskCredentialGenerator {

    GeneratedKioskCredential generate();
}
