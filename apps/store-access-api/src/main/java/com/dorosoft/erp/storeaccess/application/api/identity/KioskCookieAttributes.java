package com.dorosoft.erp.storeaccess.application.api.identity;

import java.time.Duration;

/**
 * {@code DORO_KIOSK_DEVICE} Cookie policy constants (ADR-02-013), shared by the activation Controller so it
 * stays in sync with the Cookie authentication Filter without either depending on the other's package.
 *
 * <p>{@code Max-Age} is fixed at 30 days from issuance and is never extended by a successful Kiosk business
 * request — no Sliding Renewal and no separate server-side absolute-expiry state. A Cookie that expires or is
 * lost is recovered by activating again with the existing Secret.
 */
public final class KioskCookieAttributes {

    public static final String COOKIE_NAME = "DORO_KIOSK_DEVICE";
    public static final String COOKIE_PATH = "/api/v1";
    public static final Duration MAX_AGE = Duration.ofDays(30);

    private KioskCookieAttributes() {
    }
}
