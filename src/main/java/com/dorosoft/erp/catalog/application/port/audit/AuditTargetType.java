package com.dorosoft.erp.catalog.application.port.audit;

/** 감사 대상 Aggregate 종류. 감사 기록 계약 명세의 허용값 전체. */
public enum AuditTargetType {
    ACCOUNT,
    ROLE,
    STORE_PROFILE,
    STORE_SCHEDULE,
    STORE_FEATURE_SETTINGS,
    TABLE,
    TABLE_QR_CREDENTIAL,
    TABLE_SESSION,
    TABLE_LAYOUT,
    CATEGORY,
    PRODUCT,
    ORDER,
    PAYMENT,
    INVENTORY,
    WAITING_ENTRY,
    RESERVATION,
    DAILY_CLOSING
}
