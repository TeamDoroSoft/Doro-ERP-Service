package com.dorosoft.erp.identity.infrastructure.session;

import com.dorosoft.erp.identity.infrastructure.security.SecurityProblemWriter;
import com.dorosoft.erp.identity.infrastructure.security.SessionCookieSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AbsoluteSessionExpirationFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final SecurityProblemWriter problemWriter;

    public AbsoluteSessionExpirationFilter(Clock clock, SecurityProblemWriter problemWriter) {
        this.clock = clock;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Object storedExpiry = session.getAttribute(IdentitySessionAttributes.ABSOLUTE_EXPIRES_AT);
        if (!(storedExpiry instanceof Long absoluteExpiresAt)) {
            session.invalidate();
            rejectUnlessPublicAuthenticationRequest(request, response, filterChain);
            return;
        }

        long remainingMillis = absoluteExpiresAt - clock.millis();
        if (remainingMillis < 1_000) {
            session.invalidate();
            rejectUnlessPublicAuthenticationRequest(request, response, filterChain);
            return;
        }

        int remainingSeconds = Math.toIntExact(Math.min(
                IdentitySessionAttributes.IDLE_TIMEOUT_SECONDS,
                remainingMillis / 1_000));
        session.setMaxInactiveInterval(remainingSeconds);
        filterChain.doFilter(request, response);
    }

    private void rejectUnlessPublicAuthenticationRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws IOException, ServletException {
        SessionCookieSupport.clear(response);
        String path = request.getRequestURI();
        if ("/api/v1/auth/sessions".equals(path)
                || "/api/v1/auth/sessions/current".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        problemWriter.write(request, response, 401, "AUTHENTICATION_REQUIRED", "인증 필요",
                "로그인이 필요합니다.");
    }
}
