package com.dorosoft.erp.identity.application.authentication;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record LoginResult(
        UUID accountId,
        String displayName,
        String roleCode,
        Set<String> permissions,
        boolean mustChangePassword,
        String sessionId,
        String csrfToken,
        int maxInactiveIntervalSeconds,
        Instant absoluteExpiresAt
) {
    public LoginResult {
        Objects.requireNonNull(accountId, "accountId");
        requireText(displayName, "displayName");
        requireText(roleCode, "roleCode");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
        requireText(sessionId, "sessionId");
        requireText(csrfToken, "csrfToken");
        if (maxInactiveIntervalSeconds <= 0) {
            throw new IllegalArgumentException("maxInactiveIntervalSeconds must be positive");
        }
        Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt");
    }

    @Override
    public String toString() {
        return "LoginResult[accountId=%s, roleCode=%s, sessionCredentials=redacted]"
                .formatted(accountId, roleCode);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
