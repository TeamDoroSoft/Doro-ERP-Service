package com.dorosoft.erp.identity.infrastructure.session;

import com.dorosoft.erp.identity.application.session.AuthenticatedSession;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import com.dorosoft.erp.identity.application.session.IssuedSession;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringSessionRedisRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");

    @Test
    void revokesOldPrincipalSessionsBeforeIssuingOneNewSession() {
        @SuppressWarnings("unchecked")
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        Session oldSession = session("old-session");
        Session newSession = session("new-session");
        UUID accountId = UUID.randomUUID();
        when(repository.findByPrincipalName(accountId.toString())).thenReturn(Map.of("old-session", oldSession));
        when(repository.createSession()).thenReturn(newSession);
        SpringSessionRedisRepositoryAdapter adapter = new SpringSessionRedisRepositoryAdapter(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), deterministicRandom());

        IssuedSession issued = adapter.issue(new AuthenticatedSession(
                accountId, "store-a", "EMPLOYEE", Set.of("order.read"), true));

        verify(repository).deleteById("old-session");
        verify(newSession).setMaxInactiveInterval(java.time.Duration.ofSeconds(3_600));
        verify(newSession).setAttribute(
                org.mockito.ArgumentMatchers.eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY),
                org.mockito.ArgumentMatchers.any());
        verify(repository).save(newSession);
        assertThat(issued.sessionId()).isEqualTo("new-session");
        assertThat(issued.maxInactiveIntervalSeconds()).isEqualTo(3_600);
        assertThat(issued.absoluteExpiresAt()).isEqualTo(NOW.plusSeconds(28_800));
        assertThat(java.util.Base64.getUrlDecoder().decode(issued.csrfToken())).hasSize(32);
    }

    @Test
    void propagatesRedisDeleteFailureAsFailClosedAuthenticationError() {
        @SuppressWarnings("unchecked")
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        UUID accountId = UUID.randomUUID();
        when(repository.findByPrincipalName(accountId.toString()))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        SpringSessionRedisRepositoryAdapter adapter = new SpringSessionRedisRepositoryAdapter(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), deterministicRandom());

        assertThatThrownBy(() -> adapter.revokeAll(accountId))
                .isInstanceOf(AuthenticationUnavailableException.class)
                .hasMessageNotContaining("redis unavailable");
    }

    private Session session(String id) {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    private SecureRandom deterministicRandom() {
        return new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 7);
            }
        };
    }
}
