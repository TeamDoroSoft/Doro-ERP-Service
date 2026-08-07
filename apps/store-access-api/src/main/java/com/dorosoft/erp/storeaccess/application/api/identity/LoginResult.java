package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A successful employee login (ADR-02-002/003/008). Exposes {@link #roleName()} alongside the domain
 * {@link #role()} so the presentation layer — which may not depend on {@code domain} — can read the Role as
 * a plain {@code String} without ever referencing the {@link Role} type itself.
 */
public record LoginResult(
        UUID tenantId,
        UUID storeId,
        UUID employeeId,
        Role role,
        String loginId,
        boolean passwordChangeRequired,
        Instant authenticatedAt,
        Instant absoluteExpiresAt) {

    public LoginResult {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(loginId, "loginId must not be null");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt must not be null");
        Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt must not be null");
    }

    public String roleName() {
        return role.name();
    }
}
