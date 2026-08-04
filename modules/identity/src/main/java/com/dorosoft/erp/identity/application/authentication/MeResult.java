package com.dorosoft.erp.identity.application.authentication;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MeResult(
        UUID accountId,
        String displayName,
        String status,
        String lockStatus,
        boolean mustChangePassword,
        String roleCode,
        Set<String> permissions
) {
    public MeResult {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(lockStatus, "lockStatus");
        Objects.requireNonNull(roleCode, "roleCode");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }
}
