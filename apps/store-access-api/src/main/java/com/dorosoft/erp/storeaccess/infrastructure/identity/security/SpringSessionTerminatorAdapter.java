package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSessionTerminator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.stereotype.Component;

/**
 * Deletes the Session {@link EmployeeSessionAuthenticationFilter} already resolved for this request and
 * expires the Client's Session cookie (ADR-02-002).
 */
@Component
class SpringSessionTerminatorAdapter implements EmployeeSessionTerminator {

    private final Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier;
    private final HttpSessionIdResolver sessionIdResolver;

    @Autowired
    SpringSessionTerminatorAdapter(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider,
            HttpSessionIdResolver sessionIdResolver) {
        this(sessionRepositoryProvider::getObject, sessionIdResolver);
    }

    SpringSessionTerminatorAdapter(
            Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier,
            HttpSessionIdResolver sessionIdResolver) {
        this.sessionRepositorySupplier = sessionRepositorySupplier;
        this.sessionIdResolver = sessionIdResolver;
    }

    @Override
    public void terminateCurrentSession(HttpServletRequest request, HttpServletResponse response) {
        Object resolved = request.getAttribute(EmployeeSessionAuthenticationFilter.RESOLVED_SESSION_REQUEST_ATTRIBUTE);
        if (resolved instanceof Session session) {
            sessionRepositorySupplier.get().deleteById(session.getId());
        }
        sessionIdResolver.expireSession(request, response);
    }
}
