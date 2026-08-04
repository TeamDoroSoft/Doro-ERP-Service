package com.dorosoft.erp.identity.application.session;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Allowlisted authentication snapshot stored in the tenant Identity Redis.
 */
public record AuthenticatedSession(
        UUID accountId,
        String tenantKey,
        String roleCode,
        Set<String> permissions,
        boolean mustChangePassword
) {

    public AuthenticatedSession {
        Objects.requireNonNull(accountId, "accountId must not be null");
        tenantKey = requireText(tenantKey, "tenantKey");
        roleCode = requireText(roleCode, "roleCode");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
        if (permissions.stream().anyMatch(permission -> permission == null || permission.isBlank())) {
            throw new IllegalArgumentException("permissions must contain only non-blank values");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
