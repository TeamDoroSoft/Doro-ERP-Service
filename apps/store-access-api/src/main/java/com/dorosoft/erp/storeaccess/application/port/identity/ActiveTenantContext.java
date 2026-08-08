package com.dorosoft.erp.storeaccess.application.port.identity;

import java.util.Objects;
import java.util.UUID;

/**
 * The active Tenant and its single Store, as resolved by feature 01's {@code tenantCode} lookup contract
 * (ADR-02-008). Feature 02 only consumes this shape through {@link TenantLookupPort}; it does not own
 * Tenant/Store data.
 */
public record ActiveTenantContext(UUID tenantId, UUID storeId) {

    public ActiveTenantContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
    }
}
