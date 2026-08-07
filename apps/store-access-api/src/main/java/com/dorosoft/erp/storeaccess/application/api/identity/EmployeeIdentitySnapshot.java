package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.util.UUID;

/**
 * The secret-free subset of {@link EmployeeAccount} safe to store as an idempotent replay result and to
 * return from create/reset responses (ADR-02-009: "계정 ID, 상태와 passwordChangeRequired만 포함한다").
 */
record EmployeeIdentitySnapshot(UUID employeeId, Role role, EmployeeStatus status, boolean passwordChangeRequired) {

    static EmployeeIdentitySnapshot from(EmployeeAccount account) {
        return new EmployeeIdentitySnapshot(
                account.id(), account.role(), account.status(), account.passwordChangeRequired());
    }

    String serialize() {
        return employeeId + "|" + role + "|" + status + "|" + passwordChangeRequired;
    }

    static EmployeeIdentitySnapshot deserialize(String payload) {
        String[] parts = payload.split("\\|");
        return new EmployeeIdentitySnapshot(
                UUID.fromString(parts[0]), Role.valueOf(parts[1]), EmployeeStatus.valueOf(parts[2]),
                Boolean.parseBoolean(parts[3]));
    }
}
