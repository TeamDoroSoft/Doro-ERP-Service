package com.dorosoft.erp.identity.infrastructure.security;

import com.dorosoft.erp.identity.application.authentication.IdentitySessionCookiePort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public final class ServletIdentitySessionCookieAdapter implements IdentitySessionCookiePort {
    @Override
    public String read(HttpServletRequest request) {
        return SessionCookieSupport.read(request);
    }

    @Override
    public void issue(HttpServletResponse response, String sessionId) {
        SessionCookieSupport.issue(response, sessionId);
    }

    @Override
    public void clear(HttpServletResponse response) {
        SessionCookieSupport.clear(response);
    }
}
