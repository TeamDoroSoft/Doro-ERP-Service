package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.error.LoginRateLimitedException;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitDecision;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitPort;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import com.dorosoft.erp.identity.application.session.SessionIssuer;
import com.dorosoft.erp.identity.domain.account.LoginId;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** Non-transactional orchestrator: Redis rate limiting always precedes MySQL account lookup. */
@Service
public class LoginAuthenticationService {
    private final LoginRateLimitPort rateLimit;
    private final LoginRateLimitSecurityEventRecorder rateLimitEvents;
    private final TransactionalLoginAuthenticator authenticator;
    private final SessionIssuer sessionIssuer;
    private final Clock clock;

    public LoginAuthenticationService(
            LoginRateLimitPort rateLimit,
            LoginRateLimitSecurityEventRecorder rateLimitEvents,
            TransactionalLoginAuthenticator authenticator,
            SessionIssuer sessionIssuer,
            Clock clock
    ) {
        this.rateLimit = rateLimit;
        this.rateLimitEvents = rateLimitEvents;
        this.authenticator = authenticator;
        this.sessionIssuer = sessionIssuer;
        this.clock = clock;
    }

    public LoginResult login(LoginCommand command) {
        final String normalizedLoginId;
        try {
            normalizedLoginId = LoginId.normalize(command.loginId()).value();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IdentityException(IdentityErrorCode.VALIDATION_FAILED);
        }

        LoginRateLimitDecision decision;
        try {
            decision = rateLimit.evaluate(normalizedLoginId, command.clientIpAddress());
        } catch (AuthenticationUnavailableException exception) {
            throw new IdentityException(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE, exception);
        }
        if (!decision.allowed()) {
            if (decision.securityEventRequired()) {
                try {
                    rateLimitEvents.record(decision.limitedWindowId(), command.requestId(), clock.instant());
                    rateLimit.markSecurityEventRecorded(
                            normalizedLoginId, command.clientIpAddress(), decision.limitedWindowId());
                } catch (RuntimeException exception) {
                    throw new IdentityException(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE, exception);
                }
            }
            throw new LoginRateLimitedException(decision.retryAfter());
        }

        CreatedSessionTracker createdSession = new CreatedSessionTracker();
        LoginTransactionOutcome outcome;
        try {
            outcome = authenticator.authenticate(normalizedLoginId, command, createdSession);
        } catch (RuntimeException exception) {
            if (createdSession.sessionId() != null) {
                compensate(createdSession.sessionId(), exception);
                if (exception instanceof IdentityException identityException
                        && identityException.code() == IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE) {
                    throw identityException;
                }
                throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, exception);
            }
            if (exception instanceof IdentityException identityException) {
                throw identityException;
            }
            if (exception instanceof AuthenticationUnavailableException) {
                throw new IdentityException(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE, exception);
            }
            throw new IdentityException(IdentityErrorCode.INTERNAL_SERVER_ERROR, exception);
        }

        if (outcome.failure() == LoginTransactionOutcome.Failure.INVALID_CREDENTIALS) {
            throw new IdentityException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        if (outcome.failure() == LoginTransactionOutcome.Failure.AUTHENTICATION_UNAVAILABLE) {
            throw new IdentityException(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE);
        }
        return outcome.success();
    }

    private void compensate(String sessionId, RuntimeException originalFailure) {
        try {
            sessionIssuer.delete(sessionId);
        } catch (RuntimeException compensationFailure) {
            compensationFailure.addSuppressed(originalFailure);
            throw new IdentityException(IdentityErrorCode.AUTHENTICATION_UNAVAILABLE, compensationFailure);
        }
    }
}
