package com.dorosoft.erp.storeaccess.application.api.identity;

import java.time.Duration;

/**
 * {@code DORO_KIOSK_DEVICE} Cookie policy constants (ADR-02-013), shared by the activation Controller (issues
 * the Cookie) and the infrastructure Cookie authentication Filter (reissues it on refresh) so both stay in
 * sync without either depending on the other's package.
 */
public final class KioskCookieAttributes {

    public static final String COOKIE_NAME = "DORO_KIOSK_DEVICE";
    public static final String COOKIE_PATH = "/api/v1";
    public static final Duration MAX_AGE = Duration.ofDays(30);
    public static final Duration RENEWAL_THRESHOLD = Duration.ofDays(7);

    private KioskCookieAttributes() {
    }
}
