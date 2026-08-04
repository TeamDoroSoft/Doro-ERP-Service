package com.dorosoft.erp.identity.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public final class IdentityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityProblemWriter problemWriter;

    public IdentityAuthenticationEntryPoint(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException, ServletException {
        if (SessionCookieSupport.hasSessionCookie(request)) {
            SessionCookieSupport.clear(response);
        }
        problemWriter.write(request, response, 401, "AUTHENTICATION_REQUIRED", "인증 필요",
                "로그인이 필요합니다.");
    }
}
