package com.dorosoft.erp.identity.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.regex.Pattern;

public final class SessionCookieSupport {

    public static final String COOKIE_NAME = "__Host-ERPSESSION";
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    private SessionCookieSupport() {
    }

    public static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies).anyMatch(cookie -> COOKIE_NAME.equals(cookie.getName()));
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && SAFE_SESSION_ID.matcher(value).matches())
                .findFirst()
                .orElse(null);
    }

    public static void clear(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                COOKIE_NAME + "=; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=0");
    }

    public static void issue(HttpServletResponse response, String sessionId) {
        if (sessionId == null || !SAFE_SESSION_ID.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("sessionId has an invalid format");
        }
        response.addHeader("Set-Cookie",
                COOKIE_NAME + "=" + sessionId + "; Path=/; Secure; HttpOnly; SameSite=Strict");
    }
}
