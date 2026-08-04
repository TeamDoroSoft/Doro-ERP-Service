package com.dorosoft.erp.identity.infrastructure.session;

import com.dorosoft.erp.identity.application.session.AuthenticatedSession;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import com.dorosoft.erp.identity.application.session.IssuedSession;
import com.dorosoft.erp.identity.application.session.SessionIssuer;
import com.dorosoft.erp.identity.application.session.SessionRevoker;
import com.dorosoft.erp.identity.infrastructure.security.IdentityAuthentication;
import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.identity.infrastructure.security.SessionCsrfTokenRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@Component
public final class SpringSessionRedisRepositoryAdapter implements SessionIssuer, SessionRevoker {

    private static final int CSRF_BYTES = 32;

    private final FindByIndexNameSessionRepository<Session> repository;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public SpringSessionRedisRepositoryAdapter(
            FindByIndexNameSessionRepository repository,
            Clock clock
    ) {
        this(repository, clock, new SecureRandom());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    SpringSessionRedisRepositoryAdapter(
            FindByIndexNameSessionRepository repository,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.repository = (FindByIndexNameSessionRepository<Session>) repository;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    public IssuedSession issue(AuthenticatedSession authenticatedSession) {
        try {
            revokeAll(authenticatedSession.accountId());
            Instant authenticatedAt = clock.instant();
            Instant absoluteExpiresAt = authenticatedAt.plusSeconds(IdentitySessionAttributes.ABSOLUTE_TIMEOUT_SECONDS);
            String csrfToken = generateCsrfToken();

            Session session = repository.createSession();
            session.setMaxInactiveInterval(Duration.ofSeconds(IdentitySessionAttributes.IDLE_TIMEOUT_SECONDS));
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext(authenticatedSession));
            session.setAttribute(SessionCsrfTokenRepository.ATTRIBUTE_NAME,
                    SessionCsrfTokenRepository.token(csrfToken));
            session.setAttribute(IdentitySessionAttributes.AUTHENTICATED_AT, authenticatedAt.toEpochMilli());
            session.setAttribute(IdentitySessionAttributes.ABSOLUTE_EXPIRES_AT, absoluteExpiresAt.toEpochMilli());
            repository.save(session);

            return new IssuedSession(session.getId(), csrfToken,
                    IdentitySessionAttributes.IDLE_TIMEOUT_SECONDS, absoluteExpiresAt);
        } catch (AuthenticationUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthenticationUnavailableException(exception);
        }
    }

    @Override
    public void delete(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            repository.deleteById(sessionId);
        } catch (RuntimeException exception) {
            throw new AuthenticationUnavailableException(exception);
        }
    }

    @Override
    public int revokeAll(UUID accountId) {
        try {
            Map<String, Session> sessions = repository.findByPrincipalName(accountId.toString());
            sessions.keySet().forEach(repository::deleteById);
            return sessions.size();
        } catch (RuntimeException exception) {
            throw new AuthenticationUnavailableException(exception);
        }
    }

    private SecurityContext securityContext(AuthenticatedSession session) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new IdentityAuthentication(IdentityPrincipal.from(session)));
        return context;
    }

    private String generateCsrfToken() {
        byte[] bytes = new byte[CSRF_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
