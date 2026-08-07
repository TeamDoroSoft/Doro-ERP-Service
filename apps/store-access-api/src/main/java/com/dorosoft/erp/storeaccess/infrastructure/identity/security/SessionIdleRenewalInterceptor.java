package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.api.identity.UserActivity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Renews the employee Session's 30-minute idle TTL only for requests whose handler is marked
 * {@link UserActivity} (ADR-02-003) — background auto-Polling, Health Checks and Telemetry never renew it,
 * because {@link EmployeeSessionAuthenticationFilter} only reads the Session and this interceptor is the
 * single place that ever writes {@code lastAccessedTime} back.
 *
 * <p>Runs in {@code afterCompletion} so the {@link HandlerMethod} has already been resolved by
 * {@code DispatcherServlet}, which a Servlet {@code Filter} cannot see.
 */
public class SessionIdleRenewalInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SessionIdleRenewalInterceptor.class);

    private final Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier;
    private final Clock clock;

    @Autowired
    public SessionIdleRenewalInterceptor(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider, Clock clock) {
        this(sessionRepositoryProvider::getObject, clock);
    }

    SessionIdleRenewalInterceptor(Supplier<SessionRepository<? extends Session>> sessionRepositorySupplier, Clock clock) {
        this.sessionRepositorySupplier = sessionRepositorySupplier;
        this.clock = clock;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !isUserActivity(handlerMethod)) {
            return;
        }
        Object resolved = request.getAttribute(EmployeeSessionAuthenticationFilter.RESOLVED_SESSION_REQUEST_ATTRIBUTE);
        if (!(resolved instanceof Session session)) {
            return;
        }
        try {
            session.setLastAccessedTime(clock.instant());
            save(sessionRepositorySupplier.get(), session);
        } catch (DataAccessException e) {
            log.warn("Unable to renew employee Session idle timeout");
        }
    }

    /**
     * Captures the repository's wildcard Session type so the already-fetched {@code session} (obtained from
     * this same repository by {@link EmployeeSessionAuthenticationFilter}) can be saved back through it.
     */
    @SuppressWarnings("unchecked")
    private static <S extends Session> void save(SessionRepository<S> sessionRepository, Session session) {
        sessionRepository.save((S) session);
    }

    private boolean isUserActivity(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(UserActivity.class)
                || handlerMethod.getBeanType().isAnnotationPresent(UserActivity.class);
    }
}
