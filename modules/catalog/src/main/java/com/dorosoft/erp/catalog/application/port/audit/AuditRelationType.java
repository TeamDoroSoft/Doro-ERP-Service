package com.dorosoft.erp.catalog.application.port.audit;

/** 감사 대상과 Action의 관계 종류. 감사 기록 계약 명세의 허용값 전체. */
public enum AuditRelationType {
    PRIMARY,
    SUBJECT_ACCOUNT,
    SUBJECT_ROLE,
    STORE,
    CATEGORY,
    PRODUCT,
    TABLE,
    SOURCE_TABLE,
    TARGET_TABLE,
    TABLE_SESSION,
    ORDER,
    PAYMENT,
    INVENTORY,
    WAITING_ENTRY,
    RESERVATION,
    DAILY_CLOSING
}
