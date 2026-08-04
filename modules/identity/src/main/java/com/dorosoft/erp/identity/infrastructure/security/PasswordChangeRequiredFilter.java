package com.dorosoft.erp.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/me",
            "/api/v1/auth/csrf",
            "/api/v1/auth/me/password",
            "/api/v1/auth/sessions/current"
    );

    private final SecurityProblemWriter problemWriter;

    public PasswordChangeRequiredFilter(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof IdentityPrincipal principal
                && principal.mustChangePassword()
                && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            problemWriter.write(request, response, 403, "PASSWORD_CHANGE_REQUIRED", "비밀번호 변경 필요",
                    "비밀번호를 먼저 변경해야 합니다.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
