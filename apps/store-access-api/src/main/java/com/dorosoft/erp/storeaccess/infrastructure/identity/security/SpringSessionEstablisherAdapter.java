package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.api.identity.LoginResult;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSessionEstablisher;
import com.dorosoft.erp.storeaccess.infrastructure.identity.session.EmployeeSessionAttributes;
import com.dorosoft.erp.storeaccess.infrastructure.identity.session.SessionPrincipalIndexKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.stereotype.Component;

/**
 * Creates the Redis-backed employee Session on successful login (ADR-02-002/003/005). Writes the Session
 * cookie through the application's configured {@link HttpSessionIdResolver} rather than by hand, since this
 * flow never calls {@link HttpServletRequest#getSession()} — Spring Session's own auto-cookie-writing filter
 * never sees this Session and would not otherwise send it to the Client.
 */
@Component
class SpringSessionEstablisherAdapter implements EmployeeSessionEstablisher {

    private final Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier;
    private final HttpSessionIdResolver sessionIdResolver;

    @Autowired
    SpringSessionEstablisherAdapter(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider,
            HttpSessionIdResolver sessionIdResolver) {
        this(sessionRepositoryProvider::getObject, sessionIdResolver);
    }

    SpringSessionEstablisherAdapter(
            Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier,
            HttpSessionIdResolver sessionIdResolver) {
        this.sessionRepositorySupplier = sessionRepositorySupplier;
        this.sessionIdResolver = sessionIdResolver;
    }

    @Override
    public void establish(LoginResult result, HttpServletRequest request, HttpServletResponse response) {
        EmployeePrincipal principal = new EmployeePrincipal(
                result.tenantId(), result.storeId(), result.employeeId(), result.role(), result.loginId());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new EmployeeAuthenticationToken(principal));

        String sessionId = createAndSave(sessionRepositorySupplier.get(), securityContext, result);
        sessionIdResolver.setSessionId(request, response, sessionId);
    }

    private static <S extends Session> String createAndSave(
            SessionRepository<S> sessionRepository, SecurityContext securityContext, LoginResult result) {
        S session = sessionRepository.createSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                new SessionPrincipalIndexKey(result.tenantId(), result.employeeId()).value());
        session.setAttribute(EmployeeSessionAttributes.AUTHENTICATED_AT, result.authenticatedAt());
        session.setAttribute(EmployeeSessionAttributes.ABSOLUTE_EXPIRES_AT, result.absoluteExpiresAt());
        sessionRepository.save(session);
        return session.getId();
    }
}
