package com.dorosoft.erp.storeaccess.application.port.identity;

import java.util.Optional;

/**
 * Resolves a raw {@code tenantCode} to its active Tenant/Store (ADR-02-008). Canonicalization, existence and
 * active-status checks are entirely feature 01's contract; this Port only defines the boundary feature 02
 * calls through. No production adapter exists yet — feature 01 has not been implemented in this codebase —
 * so no bean satisfies this Port outside test source, and any caller must tolerate that on any active
 * Spring profile without feature 01, login/Kiosk-activation depending on this stay dormant.
 */
public interface TenantLookupPort {

    Optional<ActiveTenantContext> resolveActiveTenantByCode(String tenantCode);
}
