package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated caller of an identity management operation: {@code tenantId}, {@code storeId} and
 * {@code role} as an employee Session would carry them (ADR-02-002). Until the employee login/Session
 * (feature 02 step 2) is wired up, callers of {@link EmployeeManagementService} build this explicitly; a
 * future Controller will populate it from the authenticated Session instead.
 */
public record ActorContext(UUID tenantId, UUID storeId, UUID employeeId, Role role) {

    public ActorContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }
}
