package com.dorosoft.erp.storeaccess.application.api.identity;

/**
 * Raw employee login request fields (ADR-02-008), before {@code tenantCode} Canonicalization (feature 01's
 * contract, applied inside {@link com.dorosoft.erp.storeaccess.application.port.identity.TenantLookupPort})
 * and {@code loginId} normalization ({@link com.dorosoft.erp.storeaccess.domain.identity.LoginId#normalize}).
 */
public record LoginCommand(String tenantCode, String loginId, String password, String clientIp) {
}
