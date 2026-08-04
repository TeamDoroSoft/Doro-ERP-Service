package com.dorosoft.erp.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.web.util.matcher.RequestMatcher;

final class IdentityCsrfRequestMatcher implements RequestMatcher {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");
    private static final String LOGIN_PATH = "/api/v1/auth/sessions";
    private static final String LOGOUT_PATH = "/api/v1/auth/sessions/current";

    @Override
    public boolean matches(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        if ("POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI())) {
            return false;
        }
        // A valid logout is protected. A stale/expired cookie has no session to bind a token to and
        // remains an idempotent 204 operation at the controller boundary.
        return !("DELETE".equals(request.getMethod())
                && LOGOUT_PATH.equals(request.getRequestURI())
                && request.getSession(false) == null);
    }
}
