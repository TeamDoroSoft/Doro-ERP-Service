package com.dorosoft.erp.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** STORE-02 임시 인증 스텁이며 Identity 모듈이 실제 세션 인증(Redis 기반)을 구현하면 교체·삭제 대상이다. */
@Component
public class StubActorAuthenticationFilter extends OncePerRequestFilter {

    static final String ACTOR_ID_HEADER = "X-Doro-Actor-Id";
    static final String ACTOR_ROLE_HEADER = "X-Doro-Actor-Role";
    static final String ACTOR_PERMISSIONS_HEADER = "X-Doro-Actor-Permissions";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String actorId = request.getHeader(ACTOR_ID_HEADER);
        String actorRole = request.getHeader(ACTOR_ROLE_HEADER);
        String permissionsHeader = request.getHeader(ACTOR_PERMISSIONS_HEADER);

        if (hasText(actorId) && hasText(actorRole) && hasText(permissionsHeader)) {
            List<SimpleGrantedAuthority> authorities = Arrays.stream(permissionsHeader.split(","))
                    .map(String::trim)
                    .filter(permission -> !permission.isEmpty())
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(actorId, null, authorities);
            authentication.setDetails(actorRole);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
