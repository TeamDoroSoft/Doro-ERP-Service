package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.port.EmployeeAccountRepository;
import com.dorosoft.erp.identity.application.port.IdentitySecurityEventRepository;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import com.dorosoft.erp.identity.application.session.SessionIssuer;
import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventOutcome;
import com.dorosoft.erp.identity.domain.securityevent.SecurityEventType;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentIdentitySessionService {
    private final SessionIssuer sessionIssuer;
    private final EmployeeAccountRepository accounts;
    private final IdentitySecurityEventRepository securityEvents;
    private final PrivacyAccessLogger privacyAccessLogger;
    private final IdentityPrivacyAccessContextFactory privacyContextFactory;
    private final Clock clock;

    public CurrentIdentitySessionService(
            SessionIssuer sessionIssuer,
            EmployeeAccountRepository accounts,
            IdentitySecurityEventRepository securityEvents,
            PrivacyAccessLogger privacyAccessLogger,
            IdentityPrivacyAccessContextFactory privacyContextFactory,
            Clock clock
    ) {
        this.sessionIssuer = sessionIssuer;
        this.accounts = accounts;
        this.securityEvents = securityEvents;
        this.privacyAccessLogger = privacyAccessLogger;
        this.privacyContextFactory = privacyContextFactory;
        this.clock = clock;
    }

    @Transactional
    public void logout(String sessionId, AuthenticatedActor actor, String requestId) {
        try {
            sessionIssuer.delete(sessionId);
        } catch (AuthenticationUnavailableException exception) {
            throw new IdentityException(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE, exception);
        }
        if (actor != null) {
            securityEvents.appendIfAbsent(SecurityEvents.event(
                    actor.accountId(), SecurityEventType.LOGOUT_SUCCEEDED,
                    SecurityEventOutcome.SUCCESS, null, requestId, clock.instant()));
        }
    }

    @Transactional
    public MeResult me(
            AuthenticatedActor actor,
            String requestId,
            String trustedClientAddress
    ) {
        EmployeeAccount account = accounts.findById(actor.accountId())
                .filter(EmployeeAccount::isActive)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.AUTHENTICATION_REQUIRED));
        var now = clock.instant();
        PrivacyAccessContext privacyContext;
        try {
            privacyContext = privacyContextFactory.create(
                    actor.tenantId(), actor.accountId(), actor.roleCode(),
                    requestId, trustedClientAddress, now);
        } catch (RuntimeException exception) {
            throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, exception);
        }
        PrivacyAccessRecords.appendEmployeeRead(privacyAccessLogger, account.employeeAccountId(), privacyContext);
        return new MeResult(
                account.employeeAccountId(), account.displayName(), account.accountStatus().name(),
                account.loginLockStatus().name(), actor.mustChangePassword(), actor.roleCode(), actor.permissions());
    }
}
