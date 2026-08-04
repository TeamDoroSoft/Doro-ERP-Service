package com.dorosoft.erp.identity.domain.securityevent;

/**
 * Identity 보안 Event 타입.
 */
public enum SecurityEventType {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGIN_REJECTED,
    LOGIN_RATE_LIMITED,
    ACCOUNT_TEMPORARILY_LOCKED,
    ACCOUNT_PERMANENTLY_LOCKED,
    LOGIN_SESSION_CREATE_FAILED,
    LOGOUT_SUCCEEDED,
    PASSWORD_CHANGED,
    PASSWORD_RESET
}

