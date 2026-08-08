package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated employee, as stored in the Spring Session-backed {@code SecurityContext}
 * (ADR-02-002/005): {@code employeeId}, {@code tenantId}, {@code storeId} and Role as separate fields, never
 * derived by parsing the Session Principal Index Key.
 */
public record EmployeePrincipal(UUID tenantId, UUID storeId, UUID employeeId, Role role, String loginId)
        implements Serializable {

    public EmployeePrincipal {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(loginId, "loginId must not be null");
    }
}
