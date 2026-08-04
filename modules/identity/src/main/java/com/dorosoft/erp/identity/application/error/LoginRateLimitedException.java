package com.dorosoft.erp.identity.application.error;

import java.time.Duration;

public final class LoginRateLimitedException extends IdentityException {
    private final long retryAfterSeconds;

    public LoginRateLimitedException(Duration retryAfter) {
        super(IdentityErrorCode.LOGIN_RATE_LIMITED);
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be positive");
        }
        this.retryAfterSeconds = Math.max(1L, (retryAfter.toMillis() + 999L) / 1_000L);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
