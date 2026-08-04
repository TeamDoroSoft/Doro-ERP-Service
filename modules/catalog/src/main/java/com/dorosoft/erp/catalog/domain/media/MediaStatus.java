package com.dorosoft.erp.catalog.domain.media;

/** Media 업로드 상태. PENDING -> READY 또는 PENDING -> REJECTED만 허용한다(ADR-007). */
public enum MediaStatus {
    PENDING,
    READY,
    REJECTED
}
