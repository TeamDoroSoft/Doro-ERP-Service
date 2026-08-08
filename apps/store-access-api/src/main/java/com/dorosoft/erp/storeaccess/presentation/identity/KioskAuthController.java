package com.dorosoft.erp.storeaccess.presentation.identity;

import com.dorosoft.erp.storeaccess.application.api.identity.KioskActivationResult;
import com.dorosoft.erp.storeaccess.application.api.identity.KioskAuthenticationService;
import com.dorosoft.erp.storeaccess.application.api.identity.KioskCookieAttributes;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiosk device activation (ADR-02-013): exchanges a one-time raw Secret for the long-lived
 * {@code DORO_KIOSK_DEVICE} Cookie. The activated Secret is never returned in the response body — only in the
 * {@code HttpOnly} Cookie itself.
 */
@RestController
@RequestMapping("/api/v1/kiosk-auth")
class KioskAuthController {

    private final KioskAuthenticationService kioskAuthenticationService;

    KioskAuthController(KioskAuthenticationService kioskAuthenticationService) {
        this.kioskAuthenticationService = kioskAuthenticationService;
    }

    @PostMapping("/activate")
    ResponseEntity<Void> activate(@Valid @RequestBody KioskActivateRequest request, HttpServletResponse servletResponse) {
        KioskActivationResult result = kioskAuthenticationService.activate(
                request.tenantCode(), request.deviceCode(), request.secret());
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie(result.cookieCredential()).toString());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie cookie(String value) {
        return ResponseCookie.from(KioskCookieAttributes.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(KioskCookieAttributes.COOKIE_PATH)
                .maxAge(KioskCookieAttributes.MAX_AGE)
                .build();
    }
}
