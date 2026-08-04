package com.dorosoft.erp.identity.application.session;

import java.time.Instant;
import java.util.Objects;

public record IssuedSession(
        String sessionId,
        String csrfToken,
        int maxInactiveIntervalSeconds,
        Instant absoluteExpiresAt
) {

    public IssuedSession {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (csrfToken == null || csrfToken.isBlank()) {
            throw new IllegalArgumentException("csrfToken must not be blank");
        }
        if (maxInactiveIntervalSeconds <= 0) {
            throw new IllegalArgumentException("maxInactiveIntervalSeconds must be positive");
        }
        Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt must not be null");
    }

    @Override
    public String toString() {
        return "IssuedSession[credentials=redacted, maxInactiveIntervalSeconds=%d, absoluteExpiresAt=%s]"
                .formatted(maxInactiveIntervalSeconds, absoluteExpiresAt);
    }
}
