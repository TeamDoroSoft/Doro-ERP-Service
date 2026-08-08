package com.dorosoft.erp.storeaccess.application.api.identity;

import java.util.UUID;

/**
 * The Controller-safe result of a successful Kiosk activation (ADR-02-013): {@code cookieCredential} is the
 * exact {@code DORO_KIOSK_DEVICE} Cookie value to set, already formatted and never persisted or logged.
 */
public record KioskActivationResult(UUID kioskDeviceId, String cookieCredential) {
}
