package com.dorosoft.erp.identity.application.ratelimit;

import java.util.UUID;

/** Must be called before any employee-account or credential lookup. */
public interface LoginRateLimitPort {

    LoginRateLimitDecision evaluate(String normalizedLoginId, ClientIpAddress clientIpAddress);

    void markSecurityEventRecorded(
            String normalizedLoginId,
            ClientIpAddress clientIpAddress,
            UUID limitedWindowId
    );
}
