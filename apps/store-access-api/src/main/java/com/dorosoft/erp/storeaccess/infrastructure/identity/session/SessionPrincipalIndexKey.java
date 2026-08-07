package com.dorosoft.erp.storeaccess.infrastructure.identity.session;

import java.util.Objects;
import java.util.UUID;

/**
 * Spring Session Principal Index key for an employee, in the {@code employee:{tenantId}:{employeeId}}
 * format (ADR-02-005). Used only for employee session registration/lookup/deletion — never as a business
 * entity ID, API resource ID, Actor ID, Idempotency scope, or Kiosk credential.
 */
public record SessionPrincipalIndexKey(UUID tenantId, UUID employeeId) {

    public SessionPrincipalIndexKey {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
    }

    public String value() {
        return "employee:" + tenantId + ":" + employeeId;
    }
}
