package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import java.time.Instant;
import java.util.UUID;

/**
 * The Controller-safe view of an {@link EmployeeAccount}: Role/status as plain {@code String}s so the
 * presentation layer — which may not depend on {@code domain} — never references those enum types.
 */
public record EmployeeSummary(
        UUID id, String loginId, String role, String status, boolean passwordChangeRequired, Instant createdAt) {

    public static EmployeeSummary from(EmployeeAccount account) {
        return new EmployeeSummary(
                account.id(), account.loginId().value(), account.role().name(), account.status().name(),
                account.passwordChangeRequired(), account.createdAt());
    }
}
