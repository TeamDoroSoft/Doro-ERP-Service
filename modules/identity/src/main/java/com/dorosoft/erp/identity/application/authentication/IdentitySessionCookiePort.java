package com.dorosoft.erp.identity.application.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IdentitySessionCookiePort {
    String read(HttpServletRequest request);

    void issue(HttpServletResponse response, String sessionId);

    void clear(HttpServletResponse response);
}
