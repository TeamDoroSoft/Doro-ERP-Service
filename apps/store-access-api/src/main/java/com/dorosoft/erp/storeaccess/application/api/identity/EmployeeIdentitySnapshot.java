package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus;
import com.dorosoft.erp.storeaccess.domain.identity.Role;
import java.util.UUID;

/**
 * The secret-free subset of {@link EmployeeAccount} safe to store as an idempotent replay result and to
 * return from create/reset responses (ADR-02-009: "계정 ID, 상태와 passwordChangeRequired만 포함한다").
 * Exposes {@link #roleName()}/{@link #statusName()} so the presentation layer — which may not depend on
 * {@code domain} — can read them as plain {@code String}s without ever referencing {@link Role}/
 * {@link EmployeeStatus} directly.
 */
public record EmployeeIdentitySnapshot(
        UUID employeeId, Role role, EmployeeStatus status, boolean passwordChangeRequired) {

    static EmployeeIdentitySnapshot from(EmployeeAccount account) {
        return new EmployeeIdentitySnapshot(
                account.id(), account.role(), account.status(), account.passwordChangeRequired());
    }

    public String roleName() {
        return role.name();
    }

    public String statusName() {
        return status.name();
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
