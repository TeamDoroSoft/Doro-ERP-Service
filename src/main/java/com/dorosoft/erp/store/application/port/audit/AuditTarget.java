package com.dorosoft.erp.store.application.port.audit;

import java.util.Objects;

/** Action의 정본 Aggregate를 가리키는 기본 대상. */
public record AuditTarget(AuditTargetType targetType, String targetId) {

    public AuditTarget {
        Objects.requireNonNull(targetType, "targetType은 필수다");
        Objects.requireNonNull(targetId, "targetId는 필수다");
        if (targetId.isBlank()) {
            throw new IllegalArgumentException("targetId는 공백일 수 없다");
        }
    }
}
