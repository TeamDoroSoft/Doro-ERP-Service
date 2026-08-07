package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.api.identity.AuthProblemCode;
import com.dorosoft.erp.storeaccess.application.api.identity.UserActivity;
import com.dorosoft.erp.storeaccess.infrastructure.identity.session.EmployeeSessionAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates each request from the Session cookie by reading Spring Session Redis directly through
 * {@link SessionRepository}, bypassing {@link HttpServletRequest#getSession()} entirely so that a plain read
 * never renews the Session's idle TTL (ADR-02-003: only genuine user activity, marked with
 * {@link UserActivity}, may do that — see {@link SessionIdleRenewalInterceptor}).
 *
 * <p>Resolves the Session ID through the same {@link HttpSessionIdResolver} used to write it
 * ({@link SpringSessionEstablisherAdapter}) rather than reading the Cookie value directly: the default
 * {@code DefaultCookieSerializer} Base64-encodes the Cookie value, so a raw read would look up the wrong ID.
 *
 * <p>Also enforces the 12-hour absolute expiration (ADR-02-003) and Fail-Closed behavior when Redis cannot
 * be reached while a Session cookie is present (ADR-02-002): a Client that believes it is authenticated must
 * never be let through as anonymous because Session state could not be checked.
 */
public class EmployeeSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSessionAuthenticationFilter.class);
    static final String RESOLVED_SESSION_REQUEST_ATTRIBUTE = "doro.identity.resolvedSession";

    private final Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier;
    private final HttpSessionIdResolver sessionIdResolver;
    private final ProblemResponseWriter problemResponseWriter;
    private final Clock clock;

    @Autowired
    public EmployeeSessionAuthenticationFilter(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider,
            HttpSessionIdResolver sessionIdResolver,
            ProblemResponseWriter problemResponseWriter,
            Clock clock) {
        this(sessionRepositoryProvider::getObject, sessionIdResolver, problemResponseWriter, clock);
    }

    EmployeeSessionAuthenticationFilter(
            Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier,
            HttpSessionIdResolver sessionIdResolver,
            ProblemResponseWriter problemResponseWriter,
            Clock clock) {
        this.sessionRepositorySupplier = sessionRepositorySupplier;
        this.sessionIdResolver = sessionIdResolver;
        this.problemResponseWriter = problemResponseWriter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        List<String> sessionIds = sessionIdResolver.resolveSessionIds(request);
        if (sessionIds.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String sessionId = sessionIds.get(0);

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
}
