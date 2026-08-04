package com.dorosoft.erp.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

public final class SessionCsrfTokenRepository implements CsrfTokenRepository {

    public static final String ATTRIBUTE_NAME = "doro.identity.csrf";
    public static final String HEADER_NAME = "X-CSRF-TOKEN";
    private static final String PARAMETER_NAME = "_csrf";
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public SessionCsrfTokenRepository() {
        this(new SecureRandom());
    }

    SessionCsrfTokenRepository(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return token(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        if (token == null) {
            if (request.getSession(false) != null) {
                request.getSession(false).removeAttribute(ATTRIBUTE_NAME);
            }
            return;
        }
        request.getSession(true).setAttribute(ATTRIBUTE_NAME, token(token.getToken()));
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        if (request.getSession(false) == null) {
            return null;
        }
        Object stored = request.getSession(false).getAttribute(ATTRIBUTE_NAME);
        return stored instanceof CsrfToken token ? token : null;
    }

    public static DefaultCsrfToken token(String value) {
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, value);
    }
}
