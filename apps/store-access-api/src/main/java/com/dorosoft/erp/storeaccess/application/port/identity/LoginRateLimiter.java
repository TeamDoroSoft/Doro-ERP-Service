package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.domain.identity.LoginId;

/**
 * Redis token bucket rate limiting for the employee login endpoint (ADR-02-007): an account-identifier scope
 * and a Client IP scope, checked before password verification. Both must be consulted; a caller rejects the
 * login attempt if either is exhausted.
 */
public interface LoginRateLimiter {

    boolean tryConsumeAccountScope(String normalizedTenantCode, LoginId loginId);

    boolean tryConsumeClientIpScope(String clientIp);
}
