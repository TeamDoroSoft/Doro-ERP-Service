package com.dorosoft.erp.store.application.port.audit;

/** 감사 기록 결과 상태. 멱등 재호출은 ALREADY_RECORDED다. */
public enum AuditWriteStatus {
    RECORDED,
    ALREADY_RECORDED
}
