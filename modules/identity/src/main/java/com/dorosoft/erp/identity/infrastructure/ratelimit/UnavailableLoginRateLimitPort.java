package com.dorosoft.erp.identity.infrastructure.ratelimit;

import com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitDecision;
import com.dorosoft.erp.identity.application.ratelimit.LoginRateLimitPort;
import com.dorosoft.erp.identity.application.session.AuthenticationUnavailableException;
import java.util.UUID;

/** Explicit fail-closed binding for an environment where a valid HMAC key is unavailable. */
public final class UnavailableLoginRateLimitPort implements LoginRateLimitPort {

    @Override
    public LoginRateLimitDecision evaluate(String normalizedLoginId, ClientIpAddress clientIpAddress) {
        throw new AuthenticationUnavailableException();
    }

    @Override
    public void markSecurityEventRecorded(
            String normalizedLoginId,
            ClientIpAddress clientIpAddress,
            UUID limitedWindowId
    ) {
        throw new AuthenticationUnavailableException();
    }
}
