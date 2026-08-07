package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.port.identity.ReauthenticationSessionManager;
import com.dorosoft.erp.storeaccess.infrastructure.identity.session.EmployeeSessionAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.stereotype.Component;

/**
 * Operates directly on the Session {@link EmployeeSessionAuthenticationFilter} already resolved for this
 * request (ADR-02-003/011), reusing {@link Session#changeSessionId()} — which {@code RedisIndexedSessionRepository}
 * implements as an in-place Redis key rename, preserving every other attribute — for Session-fixation defense.
 */
@Component
class SpringSessionReauthenticationManager implements ReauthenticationSessionManager {

    private final Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier;
    private final HttpSessionIdResolver sessionIdResolver;

    @Autowired
    SpringSessionReauthenticationManager(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider,
            HttpSessionIdResolver sessionIdResolver) {
        this(sessionRepositoryProvider::getObject, sessionIdResolver);
    }

    SpringSessionReauthenticationManager(
            Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier,
            HttpSessionIdResolver sessionIdResolver) {
        this.sessionRepositorySupplier = sessionRepositorySupplier;
        this.sessionIdResolver = sessionIdResolver;
    }

    @Override
    public int recordFailureAndGetCount(HttpServletRequest request) {
        Session session = resolvedSession(request);
        if (session == null) {
            return 0;
        }
        Integer current = session.getAttribute(EmployeeSessionAttributes.REAUTHENTICATION_FAILURE_COUNT);
        int next = (current == null ? 0 : current) + 1;
        session.setAttribute(EmployeeSessionAttributes.REAUTHENTICATION_FAILURE_COUNT, next);
        save(sessionRepositorySupplier.get(), session);
        return next;
    }

    @Override
    public void recordSuccessAndRotate(HttpServletRequest request, HttpServletResponse response, Instant authenticatedAt) {
        Session session = resolvedSession(request);
        if (session == null) {
            return;
        }
        session.setAttribute(EmployeeSessionAttributes.AUTHENTICATED_AT, authenticatedAt);
        session.setAttribute(EmployeeSessionAttributes.REAUTHENTICATION_FAILURE_COUNT, 0);
        String newSessionId = changeIdAndSave(sessionRepositorySupplier.get(), session);
        sessionIdResolver.setSessionId(request, response, newSessionId);
    }

    @Override
    public void invalidateCurrentSession(HttpServletRequest request, HttpServletResponse response) {
        Session session = resolvedSession(request);
        if (session != null) {
            sessionRepositorySupplier.get().deleteById(session.getId());
        }
        sessionIdResolver.expireSession(request, response);
    }

    private Session resolvedSession(HttpServletRequest request) {
        Object resolved = request.getAttribute(EmployeeSessionAuthenticationFilter.RESOLVED_SESSION_REQUEST_ATTRIBUTE);
        return resolved instanceof Session session ? session : null;
    }

    private static <S extends Session> void save(SessionRepository<S> sessionRepository, Session session) {
        sessionRepository.save(castTo(sessionRepository, session));
    }

    private static <S extends Session> String changeIdAndSave(SessionRepository<S> sessionRepository, Session session) {
        String newId = session.changeSessionId();
        sessionRepository.save(castTo(sessionRepository, session));
        return newId;
    }

    @SuppressWarnings("unchecked")
    private static <S extends Session> S castTo(SessionRepository<S> sessionRepository, Session session) {
        return (S) session;
    }
}
