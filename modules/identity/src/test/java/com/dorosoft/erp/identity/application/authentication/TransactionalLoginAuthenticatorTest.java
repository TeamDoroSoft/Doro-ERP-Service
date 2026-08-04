package com.dorosoft.erp.identity.application.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.api.PrivacyAccessResult;
import com.dorosoft.erp.identity.application.port.CredentialRepository;
import com.dorosoft.erp.identity.application.port.EmployeeAccountRepository;
import com.dorosoft.erp.identity.application.port.IdentitySecurityEventRepository;
import com.dorosoft.erp.identity.application.port.PasswordHasher;
import com.dorosoft.erp.identity.application.port.RoleRepository;
import com.dorosoft.erp.identity.application.session.IssuedSession;
import com.dorosoft.erp.identity.application.session.SessionIssuer;
import com.dorosoft.erp.identity.application.session.SessionRevoker;
import com.dorosoft.erp.identity.domain.account.AccountStatus;
import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import com.dorosoft.erp.identity.domain.account.LoginLockStatus;
import com.dorosoft.erp.identity.domain.credential.Credential;
import com.dorosoft.erp.identity.domain.role.AssignmentSource;
import com.dorosoft.erp.identity.domain.role.Role;
import com.dorosoft.erp.identity.domain.role.RoleAssignment;
import com.dorosoft.erp.identity.domain.role.RoleCode;
import com.dorosoft.erp.identity.domain.securityevent.IdentitySecurityEvent;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventType;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionalLoginAuthenticatorTest {
    private static final String HASH =
            "{argon2id-v1}$argon2id$v=19$m=19456,t=2,p=1$YWJjZA==$ZWZnaA==";
    private static final Instant NOW = Instant.parse("2026-08-04T02:00:00Z");

    private final EmployeeAccountRepository accounts = mock(EmployeeAccountRepository.class);
    private final CredentialRepository credentials = mock(CredentialRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final SessionIssuer sessionIssuer = mock(SessionIssuer.class);
    private final SessionRevoker sessionRevoker = mock(SessionRevoker.class);
    private final IdentitySecurityEventRepository securityEvents = mock(IdentitySecurityEventRepository.class);
    private final PrivacyAccessLogger privacyLogger = mock(PrivacyAccessLogger.class);
    private final IdentityPrivacyAccessContextFactory contextFactory =
            mock(IdentityPrivacyAccessContextFactory.class);
    private final UUID accountId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private final EmployeeAccount account = EmployeeAccount.bootstrapActiveAccount(accountId, "staff01", "직원 1");
    private final Credential credential = Credential.initial(accountId, HASH, NOW.minusSeconds(60));
    private TransactionalLoginAuthenticator authenticator;
    private LoginCommand command;

    @BeforeEach
    void setUp() {
        authenticator = new TransactionalLoginAuthenticator(
                accounts, credentials, roles, passwordHasher, sessionIssuer, sessionRevoker,
                securityEvents, privacyLogger, contextFactory, new IdentityTenant("tenant-a"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        command = new LoginCommand(
                "staff01", "secret", ClientIpFixture.address(), "192.0.2.10", "req-1");
    }

    @Test
    void successfulLoginCreatesTheApprovedSnapshotAndBothRequiredRecords() {
        Role role = new Role(roleId, new RoleCode("EMPLOYEE"), "직원", true, 0,
                Set.of("order.read", "order.status.update"));
        when(accounts.findByNormalizedLoginIdForUpdate("staff01")).thenReturn(Optional.of(account));
        when(credentials.findByAccountIdForUpdate(accountId)).thenReturn(Optional.of(credential));
        when(passwordHasher.matches("secret", HASH)).thenReturn(true);
        when(roles.findAssignment(accountId)).thenReturn(Optional.of(new RoleAssignment(
                accountId, roleId, new RoleCode("EMPLOYEE"), UUID.randomUUID(),
                AssignmentSource.ADMIN, NOW.minusSeconds(100))));
        when(roles.findByCodeForUpdate("EMPLOYEE")).thenReturn(Optional.of(role));
        when(sessionIssuer.issue(any())).thenReturn(new IssuedSession(
                "opaque-session", "csrf", 3600, NOW.plusSeconds(28_800)));
        when(contextFactory.create(anyString(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(context());
        when(privacyLogger.append(any(), any())).thenReturn(
                PrivacyAccessResult.success(UUID.randomUUID(), NOW));

        var outcome = authenticator.authenticate("staff01", command, new CreatedSessionTracker());

        assertThat(outcome.failure()).isNull();
        assertThat(outcome.success().accountId()).isEqualTo(accountId);
        assertThat(outcome.success().permissions()).containsExactlyInAnyOrder(
                "order.read", "order.status.update");
        verify(sessionIssuer).issue(any());
        verify(privacyLogger).append(any(), any());
        ArgumentCaptor<IdentitySecurityEvent> events = ArgumentCaptor.forClass(IdentitySecurityEvent.class);
        verify(securityEvents).appendIfAbsent(events.capture());
        assertThat(events.getValue().eventType()).isEqualTo(SecurityEventType.LOGIN_SUCCEEDED);
    }

    @Test
    void missingAccountProducesOnlyTheGeneralizedCredentialMismatchEvent() {
        when(accounts.findByNormalizedLoginIdForUpdate("staff01")).thenReturn(Optional.empty());

        var outcome = authenticator.authenticate("staff01", command, new CreatedSessionTracker());

        assertThat(outcome.failure()).isEqualTo(LoginTransactionOutcome.Failure.INVALID_CREDENTIALS);
        verify(passwordHasher, never()).matches(any(), anyString());
        ArgumentCaptor<IdentitySecurityEvent> event = ArgumentCaptor.forClass(IdentitySecurityEvent.class);
        verify(securityEvents).appendIfAbsent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(SecurityEventType.LOGIN_FAILED);
        assertThat(event.getValue().accountId()).isNull();
    }

    @Test
    void fifthCredentialFailureLocksAndRevokesAllSessionsAtomically() {
        EmployeeAccount fourFailures = new EmployeeAccount(
                accountId, "staff01", "직원 1", AccountStatus.ACTIVE, LoginLockStatus.NONE,
                4, 0, null, null, 0);
        when(accounts.findByNormalizedLoginIdForUpdate("staff01")).thenReturn(Optional.of(fourFailures));
        when(credentials.findByAccountIdForUpdate(accountId)).thenReturn(Optional.of(credential));
        when(passwordHasher.matches("secret", HASH)).thenReturn(false);
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var outcome = authenticator.authenticate("staff01", command, new CreatedSessionTracker());

        assertThat(outcome.failure()).isEqualTo(LoginTransactionOutcome.Failure.INVALID_CREDENTIALS);
        verify(sessionRevoker).revokeAll(accountId);
        ArgumentCaptor<EmployeeAccount> saved = ArgumentCaptor.forClass(EmployeeAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().loginLockStatus()).isEqualTo(LoginLockStatus.TEMPORARY);
        ArgumentCaptor<IdentitySecurityEvent> events = ArgumentCaptor.forClass(IdentitySecurityEvent.class);
        verify(securityEvents, org.mockito.Mockito.times(2)).appendIfAbsent(events.capture());
        assertThat(events.getAllValues()).extracting(IdentitySecurityEvent::eventType)
                .containsExactly(SecurityEventType.LOGIN_FAILED, SecurityEventType.ACCOUNT_TEMPORARILY_LOCKED);
    }

    @Test
    void activeTemporaryLockDoesNotHashOrIncreaseTheFailureCount() {
        EmployeeAccount locked = new EmployeeAccount(
                accountId, "staff01", "직원 1", AccountStatus.ACTIVE, LoginLockStatus.TEMPORARY,
                5, 1, NOW.minusSeconds(60), NOW.plusSeconds(240), 0);
        when(accounts.findByNormalizedLoginIdForUpdate("staff01")).thenReturn(Optional.of(locked));

        var outcome = authenticator.authenticate("staff01", command, new CreatedSessionTracker());

        assertThat(outcome.failure()).isEqualTo(LoginTransactionOutcome.Failure.INVALID_CREDENTIALS);
        verify(passwordHasher, never()).matches(any(), anyString());
        verify(accounts, never()).save(any());
    }

    private PrivacyAccessContext context() {
        return new PrivacyAccessContext(
                "tenant-a", "EMPLOYEE", accountId, "EMPLOYEE", "req-1",
                "Y2lwaGVydGV4dA==", "v1", NOW);
    }

    private static final class ClientIpFixture {
        private static com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress address() {
            return com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress.of(
                    InetAddress.ofLiteral("192.0.2.10"));
        }
    }
}
