package com.dorosoft.erp.identity.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.api.PrivacyAccessResult;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.port.EmployeeAccountRepository;
import com.dorosoft.erp.identity.application.port.IdentitySecurityEventRepository;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import com.dorosoft.erp.identity.application.session.SessionIssuer;
import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CurrentIdentitySessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T03:00:00Z");
    private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
    private final EmployeeAccountRepository accounts = mock(EmployeeAccountRepository.class);
    private final IdentitySecurityEventRepository securityEvents = mock(IdentitySecurityEventRepository.class);
    private final PrivacyAccessLogger privacyLogger = mock(PrivacyAccessLogger.class);
    private final IdentityPrivacyAccessContextFactory contextFactory =
            mock(IdentityPrivacyAccessContextFactory.class);
    private CurrentIdentitySessionService service;

    @BeforeEach
    void setUp() {
        service = new CurrentIdentitySessionService(
                sessionIssuer, accounts, securityEvents, privacyLogger, contextFactory,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void staleLogoutIsIdempotentAndDoesNotInventASecurityEvent() {
        service.logout("stale-session", null, "req-1");

        verify(sessionIssuer).delete("stale-session");
        org.mockito.Mockito.verifyNoInteractions(securityEvents);
    }

    @Test
    void sessionStoreFailureUsesTheStableAuthenticationUnavailableCode() {
        org.mockito.Mockito.doThrow(new AuthenticationUnavailableException())
                .when(sessionIssuer).delete("session");

        assertThatThrownBy(() -> service.logout("session", null, "req-1"))
                .isInstanceOfSatisfying(IdentityException.class,
                        error -> assertThat(error.code()).isEqualTo(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE));
    }

    @Test
    void meAppendsPrivacyBeforeReturningTheProjection() {
        UUID accountId = UUID.randomUUID();
        var actor = new AuthenticatedActor(
                accountId, "tenant-a", "EMPLOYEE", Set.of("order.read"), false);
        when(accounts.findById(accountId)).thenReturn(Optional.of(
                EmployeeAccount.bootstrapActiveAccount(accountId, "staff01", "직원 1")));
        when(contextFactory.create(anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new PrivacyAccessContext(
                        "tenant-a", "EMPLOYEE", accountId, "EMPLOYEE", "req-1",
                        "Y2lwaGVydGV4dA==", "v1", NOW));
        when(privacyLogger.append(any(), any())).thenReturn(
                PrivacyAccessResult.success(UUID.randomUUID(), NOW));

        var result = service.me(actor, "req-1", "192.0.2.10");

        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.displayName()).isEqualTo("직원 1");
        verify(privacyLogger).append(any(), any());
    }
}
