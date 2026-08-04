package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.port.CredentialRepository;
import com.dorosoft.erp.identity.application.port.EmployeeAccountRepository;
import com.dorosoft.erp.identity.application.port.IdentitySecurityEventRepository;
import com.dorosoft.erp.identity.application.port.PasswordHasher;
import com.dorosoft.erp.identity.application.port.RoleRepository;
import com.dorosoft.erp.identity.application.session.AuthenticatedSession;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import com.dorosoft.erp.identity.application.session.IssuedSession;
import com.dorosoft.erp.identity.application.session.SessionIssuer;
import com.dorosoft.erp.identity.application.session.SessionRevoker;
import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import com.dorosoft.erp.identity.domain.account.LoginLockStatus;
import com.dorosoft.erp.identity.domain.credential.Credential;
import com.dorosoft.erp.identity.domain.role.Role;
import com.dorosoft.erp.identity.domain.role.RoleAssignment;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventFailureClass;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventOutcome;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventType;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalLoginAuthenticator {
    private final EmployeeAccountRepository accounts;
    private final CredentialRepository credentials;
    private final RoleRepository roles;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessionIssuer;
    private final SessionRevoker sessionRevoker;
    private final IdentitySecurityEventRepository securityEvents;
    private final PrivacyAccessLogger privacyAccessLogger;
    private final IdentityPrivacyAccessContextFactory privacyContextFactory;
    private final IdentityTenant tenant;
    private final Clock clock;

    public TransactionalLoginAuthenticator(
            EmployeeAccountRepository accounts,
            CredentialRepository credentials,
            RoleRepository roles,
            PasswordHasher passwordHasher,
            SessionIssuer sessionIssuer,
            SessionRevoker sessionRevoker,
            IdentitySecurityEventRepository securityEvents,
            PrivacyAccessLogger privacyAccessLogger,
            IdentityPrivacyAccessContextFactory privacyContextFactory,
            IdentityTenant tenant,
            Clock clock
    ) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.roles = roles;
        this.passwordHasher = passwordHasher;
        this.sessionIssuer = sessionIssuer;
        this.sessionRevoker = sessionRevoker;
        this.securityEvents = securityEvents;
        this.privacyAccessLogger = privacyAccessLogger;
        this.privacyContextFactory = privacyContextFactory;
        this.tenant = tenant;
        this.clock = clock;
    }

    @Transactional
    public LoginTransactionOutcome authenticate(
            String normalizedLoginId,
            LoginCommand command,
            CreatedSessionTracker createdSession
    ) {
        Instant now = clock.instant();
        Optional<EmployeeAccount> found = accounts.findByNormalizedLoginIdForUpdate(normalizedLoginId);
        if (found.isEmpty()) {
            appendCredentialMismatch(null, command.requestId(), now);
            return LoginTransactionOutcome.failure(LoginTransactionOutcome.Failure.INVALID_CREDENTIALS);
        }

        EmployeeAccount account = found.orElseThrow();
        EmployeeAccount resolved = account.clearTemporaryIfExpired(now);

        if (!resolved.isActive()) {
            appendRejected(resolved, SecurityEventFailureClass.ACCOUNT_INACTIVE, command.requestId(), now);
            return LoginTransactionOutcome.failure(LoginTransactionOutcome.Failure.INVALID_CREDENTIALS);
        }
        if (resolved.isLocked(now)) {
            SecurityEventFailureClass failureClass = resolved.loginLockStatus() == LoginLockStatus.TEMPORARY
                    ? SecurityEventFailureClass.TEMPORARY_LOCK_ACTIVE
                    : SecurityEventFailureClass.PERMANENT_LOCK_ACTIVE;
            appendRejected(resolved, failureClass, command.requestId(), now);
            return LoginTransactionOutcome.failure(LoginTransactionOutcome.Failure.INVALID_CREDENTIALS);
        }

        Credential credential = credentials.findByAccountIdForUpdate(resolved.employeeAccountId()).orElse(null);
        String normalizedPassword = Normalizer.normalize(command.password(), Normalizer.Form.NFC);
        if (credential == null || !passwordHasher.matches(normalizedPassword, credential.passwordHash())) {
            processCredentialMismatch(resolved, command.requestId(), now);
            return LoginTransactionOutcome.failure(LoginTransactionOutcome.Failure.INVALID_CREDENTIALS);
        }

        RoleAssignment assignment = roles.findAssignment(resolved.employeeAccountId())
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE));
        Role role = roles.findByCodeForUpdate(assignment.roleCode().value())
                .filter(Role::active)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE));

        if (resolved.failedLoginCount() != 0 || resolved.temporaryLockCount() != 0
                || resolved.loginLockStatus() != LoginLockStatus.NONE) {
            resolved = accounts.save(resolved.withSuccessfulLogin());
        }
        if (passwordHasher.needsRehash(credential.passwordHash())) {
            String nextHash = passwordHasher.hash(normalizedPassword);
            credential = credentials.save(credential.rehashAfterSuccessfulLogin(nextHash, now));
        }

        IssuedSession issuedSession;
        try {
            issuedSession = sessionIssuer.issue(new AuthenticatedSession(
                    resolved.employeeAccountId(), tenant.value(), role.code().value(),
                    role.permissionCodes(), credential.mustChangePassword()
            ));
        } catch (AuthenticationUnavailableException exception) {
            securityEvents.appendIfAbsent(SecurityEvents.event(
                    resolved.employeeAccountId(),
                    SecurityEventType.LOGIN_SESSION_CREATE_FAILED,
                    SecurityEventOutcome.FAILURE,
                    SecurityEventFailureClass.SESSION_STORE_UNAVAILABLE,
                    command.requestId(),
                    now
            ));
            return LoginTransactionOutcome.failure(LoginTransactionOutcome.Failure.AUTHENTICATION_UNAVAILABLE);
        }
        createdSession.created(issuedSession.sessionId());

        securityEvents.appendIfAbsent(SecurityEvents.event(
                resolved.employeeAccountId(), SecurityEventType.LOGIN_SUCCEEDED,
                SecurityEventOutcome.SUCCESS, null, command.requestId(), now));

        PrivacyAccessContext privacyContext;
        try {
            privacyContext = privacyContextFactory.create(
                    tenant.value(), resolved.employeeAccountId(), role.code().value(),
                    command.requestId(), command.trustedClientAddress(), now);
        } catch (RuntimeException exception) {
            throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, exception);
        }
        PrivacyAccessRecords.appendEmployeeRead(
                privacyAccessLogger, resolved.employeeAccountId(), privacyContext);

        return LoginTransactionOutcome.success(new LoginResult(
                resolved.employeeAccountId(), resolved.displayName(), role.code().value(), role.permissionCodes(),
                credential.mustChangePassword(), issuedSession.sessionId(), issuedSession.csrfToken(),
                issuedSession.maxInactiveIntervalSeconds(), issuedSession.absoluteExpiresAt()
        ));
    }

    private void processCredentialMismatch(EmployeeAccount account, String requestId, Instant now) {
        LoginLockStatus before = account.loginLockStatus();
        EmployeeAccount updated = account.withFailedLoginAttempt(now);
        boolean newlyLocked = before == LoginLockStatus.NONE
                && updated.loginLockStatus() != LoginLockStatus.NONE;
        if (newlyLocked) {
            sessionRevoker.revokeAll(account.employeeAccountId());
        }
        accounts.save(updated);
        appendCredentialMismatch(account.employeeAccountId(), requestId, now);
        if (newlyLocked) {
            SecurityEventType type = updated.loginLockStatus() == LoginLockStatus.TEMPORARY
                    ? SecurityEventType.ACCOUNT_TEMPORARILY_LOCKED
                    : SecurityEventType.ACCOUNT_PERMANENTLY_LOCKED;
            securityEvents.appendIfAbsent(SecurityEvents.event(
                    account.employeeAccountId(), type, SecurityEventOutcome.SUCCESS, null, requestId, now));
        }
    }

    private void appendCredentialMismatch(java.util.UUID accountId, String requestId, Instant now) {
        securityEvents.appendIfAbsent(SecurityEvents.event(
                accountId,
                SecurityEventType.LOGIN_FAILED,
                SecurityEventOutcome.FAILURE,
                SecurityEventFailureClass.CREDENTIAL_MISMATCH,
                requestId,
                now
        ));
    }

    private void appendRejected(
            EmployeeAccount account,
            SecurityEventFailureClass failureClass,
            String requestId,
            Instant now
    ) {
        securityEvents.appendIfAbsent(SecurityEvents.event(
                account.employeeAccountId(), SecurityEventType.LOGIN_REJECTED,
                SecurityEventOutcome.DENIED, failureClass, requestId, now));
    }
}
