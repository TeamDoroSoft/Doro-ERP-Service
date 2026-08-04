package com.dorosoft.erp.identity.application.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record LoginRateLimitDecision(
        boolean allowed,
        Duration retryAfter,
        boolean securityEventRequired,
        UUID limitedWindowId
) {

    public LoginRateLimitDecision {
        Objects.requireNonNull(retryAfter, "retryAfter must not be null");
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must not be negative");
        }
        if (allowed && (!retryAfter.isZero() || securityEventRequired || limitedWindowId != null)) {
            throw new IllegalArgumentException("allowed decision cannot contain limited-window data");
        }
        if (!allowed && (retryAfter.isZero() || limitedWindowId == null)) {
            throw new IllegalArgumentException("limited decision requires retryAfter and limitedWindowId");
        }
    }

    public Optional<UUID> limitedWindow() {
        return Optional.ofNullable(limitedWindowId);
    }

    public static LoginRateLimitDecision allow() {
        return new LoginRateLimitDecision(true, Duration.ZERO, false, null);
    }

    public static LoginRateLimitDecision reject(Duration retryAfter, boolean eventRequired, UUID windowId) {
        return new LoginRateLimitDecision(false, retryAfter, eventRequired, windowId);
    }
}
