package com.dorosoft.erp.audit.application.api;

import java.time.Instant;
import java.util.Objects;

/** 감사 기록 결과. ALREADY_RECORDED면 auditId는 기존 기록의 ID다. */
public record AuditWriteResult(
        AuditWriteStatus status,
        String auditId,
        Instant occurredAt) {

    public AuditWriteResult {
        Objects.requireNonNull(status, "status는 필수다");
        Objects.requireNonNull(auditId, "auditId는 필수다");
        if (auditId.isBlank()) {
            throw new IllegalArgumentException("auditId는 공백일 수 없다");
        }
        Objects.requireNonNull(occurredAt, "occurredAt은 필수다");
    }
}
