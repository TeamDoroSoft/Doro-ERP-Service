package com.dorosoft.erp.identity.infrastructure.session;

import com.dorosoft.erp.identity.infrastructure.security.SecurityProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Wraps Spring Session so Redis read/write failures never become an authentication bypass. */
@Component
@Order(SessionRepositoryFilter.DEFAULT_ORDER - 1)
public final class RedisSessionFailureFilter extends OncePerRequestFilter {

    private final SecurityProblemWriter problemWriter;

    public RedisSessionFailureFilter(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            if (!causedByRedisDataAccess(exception)) {
                throw exception;
            }
            problemWriter.write(request, response, 503, "AUTHENTICATION_UNAVAILABLE", "인증 일시 중단",
                    "인증 서비스를 잠시 사용할 수 없습니다.");
        }
    }

    private boolean causedByRedisDataAccess(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof DataAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
