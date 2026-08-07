package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.api.identity.AuthProblemCode;
import com.dorosoft.erp.storeaccess.infrastructure.identity.session.EmployeeSessionAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates each request from the Session cookie by reading Spring Session Redis directly through
 * {@link SessionRepository}, bypassing {@link HttpServletRequest#getSession()} entirely so that a plain read
 * never renews the Session's idle TTL (ADR-02-003: only genuine user activity, marked with
 * {@link UserActivity}, may do that — see {@link SessionIdleRenewalInterceptor}).
 *
 * <p>Also enforces the 12-hour absolute expiration (ADR-02-003) and Fail-Closed behavior when Redis cannot
 * be reached while a Session cookie is present (ADR-02-002): a Client that believes it is authenticated must
 * never be let through as anonymous because Session state could not be checked.
 */
public class EmployeeSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSessionAuthenticationFilter.class);
    static final String SESSION_COOKIE_NAME = "SESSION";
    static final String RESOLVED_SESSION_REQUEST_ATTRIBUTE = "doro.identity.resolvedSession";

    private final Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier;
    private final ProblemResponseWriter problemResponseWriter;
    private final Clock clock;

    @Autowired
    public EmployeeSessionAuthenticationFilter(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider,
            ProblemResponseWriter problemResponseWriter,
            Clock clock) {
        this(sessionRepositoryProvider::getObject, problemResponseWriter, clock);
    }

    EmployeeSessionAuthenticationFilter(
            Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier,
            ProblemResponseWriter problemResponseWriter,
            Clock clock) {
        this.sessionRepositorySupplier = sessionRepositorySupplier;
        this.problemResponseWriter = problemResponseWriter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String sessionId = readSessionCookie(request);
        if (sessionId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Session session;
        try {
            session = sessionRepositorySupplier.get().findById(sessionId);
        } catch (DataAccessException e) {
            log.warn("Unable to validate employee Session: Session store unavailable");
            problemResponseWriter.write(request, response, AuthProblemCode.SESSION_VALIDATION_UNAVAILABLE);
            return;
        }

        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Object storedContext = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(storedContext instanceof SecurityContext securityContext)
                || !(securityContext.getAuthentication() instanceof EmployeeAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        Instant absoluteExpiresAt = session.getAttribute(EmployeeSessionAttributes.ABSOLUTE_EXPIRES_AT);
        if (absoluteExpiresAt != null && !clock.instant().isBefore(absoluteExpiresAt)) {
            try {
                sessionRepositorySupplier.get().deleteById(sessionId);
            } catch (DataAccessException e) {
                log.warn("Unable to delete absolutely-expired employee Session");
            }
            problemResponseWriter.write(request, response, AuthProblemCode.SESSION_ABSOLUTE_EXPIRED);
            return;
        }

        try {
            SecurityContextHolder.getContext().setAuthentication(securityContext.getAuthentication());
            request.setAttribute(RESOLVED_SESSION_REQUEST_ATTRIBUTE, session);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
