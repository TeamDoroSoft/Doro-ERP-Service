package com.dorosoft.erp.identity.application.authentication;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable authentication snapshot restored from Redis. */
public record AuthenticatedActor(
        UUID accountId,
        String tenantId,
        String roleCode,
        Set<String> permissions,
        boolean mustChangePassword
) {
    public AuthenticatedActor {
        Objects.requireNonNull(accountId, "accountId");
        requireText(tenantId, "tenantId");
        requireText(roleCode, "roleCode");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
