package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.application.api.identity.AuthProblemCode;
import com.dorosoft.erp.storeaccess.application.api.identity.KioskAuthenticationResult;
import com.dorosoft.erp.storeaccess.application.api.identity.KioskAuthenticationService;
import com.dorosoft.erp.storeaccess.application.api.identity.KioskCookieAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates each request carrying a {@code DORO_KIOSK_DEVICE} Cookie, re-verifying Credential, device
 * status and Tenant/Store active status against PostgreSQL on every request rather than trusting any cached
 * result (ADR-02-013: "MVP에서는 Kiosk 인증용 Redis Session 또는 인증 결과 Cache를 두지 않는다").
 *
 * <p>Runs immediately after {@link EmployeeSessionAuthenticationFilter} in the chain (wired in
 * {@code SecurityConfig} via {@code addFilterAfter}), so that if the employee Filter already authenticated a
 * valid employee Session on this same request, a simultaneously-valid Kiosk Cookie is rejected as
 * {@link AuthProblemCode#AMBIGUOUS_AUTHENTICATION} rather than one Principal silently winning (ADR-02-013:
 * "어느 Principal도 임의로 우선하지 않고 401 AMBIGUOUS_AUTHENTICATION으로 거절한다").
 */
public class KioskDeviceAuthenticationFilter extends OncePerRequestFilter {

    private final KioskAuthenticationService kioskAuthenticationService;
    private final ProblemResponseWriter problemResponseWriter;

    public KioskDeviceAuthenticationFilter(
            KioskAuthenticationService kioskAuthenticationService, ProblemResponseWriter problemResponseWriter) {
        this.kioskAuthenticationService = kioskAuthenticationService;
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cookieValue = readCookie(request);
        if (cookieValue == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean employeeAlreadyAuthenticated =
                SecurityContextHolder.getContext().getAuthentication() instanceof EmployeeAuthenticationToken;

        KioskAuthenticationResult result;
        try {
            result = kioskAuthenticationService.authenticateCookie(cookieValue);
        } catch (ProblemAwareException e) {
            if (employeeAlreadyAuthenticated) {
                // ADR-02-013 only rejects as ambiguous when BOTH Credentials are valid; a stray/invalid Kiosk
                // Cookie alongside an already-valid employee Session is harmless noise, not a real conflict.
                filterChain.doFilter(request, response);
                return;
            }
            if (e.code() == AuthProblemCode.KIOSK_AUTHENTICATION_FAILED) {
                response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie().toString());
            }
            problemResponseWriter.write(request, response, e.code());
            return;
        }

        if (employeeAlreadyAuthenticated) {
            problemResponseWriter.write(request, response, AuthProblemCode.AMBIGUOUS_AUTHENTICATION);
            return;
        }

        KioskPrincipal principal = new KioskPrincipal(
                result.tenantId(), result.storeId(), result.kioskDeviceId(), result.deviceCode());
        try {
            SecurityContextHolder.getContext().setAuthentication(new KioskAuthenticationToken(principal));
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (KioskCookieAttributes.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(KioskCookieAttributes.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(KioskCookieAttributes.COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
