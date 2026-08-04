package com.dorosoft.erp.identity.application.authentication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.error.LoginRateLimitedException;
import com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitDecision;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitPort;
import com.dorosoft.erp.identity.application.session.SessionIssuer;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAuthenticationServiceTest {
    private final LoginRateLimitPort rateLimit = mock(LoginRateLimitPort.class);
    private final LoginRateLimitSecurityEventRecorder rateLimitEvents =
            mock(LoginRateLimitSecurityEventRecorder.class);
    private final TransactionalLoginAuthenticator authenticator = mock(TransactionalLoginAuthenticator.class);
    private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-04T01:00:00Z"), ZoneOffset.UTC);
    private LoginAuthenticationService service;
    private LoginCommand command;

    @BeforeEach
    void setUp() throws Exception {
        service = new LoginAuthenticationService(rateLimit, rateLimitEvents, authenticator, sessionIssuer, clock);
        command = new LoginCommand(
                " Staff01 ", "secret", ClientIpAddress.of(InetAddress.ofLiteral("192.0.2.7")),
                "192.0.2.7", "req-1");
    }

    @Test
    void rateLimitRejectsBeforeStartingTheAccountTransaction() {
        UUID windowId = UUID.randomUUID();
        when(rateLimit.evaluate(anyString(), any()))
                .thenReturn(LoginRateLimitDecision.reject(Duration.ofMillis(1_001), false, windowId));

        assertThatThrownBy(() -> service.login(command))
                .isInstanceOfSatisfying(LoginRateLimitedException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.retryAfterSeconds()).isEqualTo(2));

        verify(authenticator, never()).authenticate(anyString(), any(), any());
    }

    @Test
    void recordsTheFirstLimitedWindowBeforeAcknowledgingRedis() {
        UUID windowId = UUID.randomUUID();
        when(rateLimit.evaluate(anyString(), any()))
                .thenReturn(LoginRateLimitDecision.reject(Duration.ofSeconds(3), true, windowId));

        assertThatThrownBy(() -> service.login(command)).isInstanceOf(LoginRateLimitedException.class);

        var ordered = inOrder(rateLimitEvents, rateLimit);
        ordered.verify(rateLimitEvents).record(windowId, "req-1", clock.instant());
        ordered.verify(rateLimit).markSecurityEventRecorded("staff01", command.clientIpAddress(), windowId);
        verify(authenticator, never()).authenticate(anyString(), any(), any());
    }

    @Test
    void deletesANewSessionWhenTheMysqlOrPrivacyTransactionFails() {
        when(rateLimit.evaluate(anyString(), any())).thenReturn(LoginRateLimitDecision.allow());
        when(authenticator.authenticate(anyString(), any(), any())).thenAnswer(invocation -> {
            CreatedSessionTracker tracker = invocation.getArgument(2);
            tracker.created("opaque-session");
            throw new IllegalStateException("commit failed");
        });

        assertThatThrownBy(() -> service.login(command))
                .isInstanceOfSatisfying(IdentityException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.code())
                                .isEqualTo(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE));

        verify(sessionIssuer).delete("opaque-session");
    }
}
