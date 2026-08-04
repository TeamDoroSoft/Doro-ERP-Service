package com.dorosoft.erp.identity.domain.securityevent;

/**
 * 보안 Event 내부 실패 구분자.
 */
public enum SecurityEventFailureClass {
    CREDENTIAL_MISMATCH,
    ACCOUNT_INACTIVE,
    TEMPORARY_LOCK_ACTIVE,
    PERMANENT_LOCK_ACTIVE,
    SESSION_STORE_UNAVAILABLE
}
