package com.dorosoft.erp.identity.infrastructure.security;

import com.dorosoft.erp.identity.application.session.AuthenticatedSession;
import java.security.Principal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record IdentityPrincipal(
        UUID accountId,
        String tenantKey,
        String roleCode,
        Set<String> permissions,
        boolean mustChangePassword
) implements Principal {

    public IdentityPrincipal {
        Objects.requireNonNull(accountId, "accountId must not be null");
        tenantKey = requireText(tenantKey, "tenantKey");
        roleCode = requireText(roleCode, "roleCode");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
    }

    public static IdentityPrincipal from(AuthenticatedSession session) {
        return new IdentityPrincipal(
                session.accountId(),
                session.tenantKey(),
                session.roleCode(),
                session.permissions(),
                session.mustChangePassword());
    }

    @Override
    public String getName() {
        return accountId.toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
