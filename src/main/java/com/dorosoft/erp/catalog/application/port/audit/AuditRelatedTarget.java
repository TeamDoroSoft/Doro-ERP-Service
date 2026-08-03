package com.dorosoft.erp.catalog.application.port.audit;

import java.util.Objects;

/** 기본 대상 외에 의미가 있는 관련 대상. 같은 (relationType, targetType, targetId)는 중복하지 않는다. */
public record AuditRelatedTarget(
        AuditRelationType relationType,
        AuditTargetType targetType,
        String targetId) {

    public AuditRelatedTarget {
        Objects.requireNonNull(relationType, "relationType은 필수다");
        Objects.requireNonNull(targetType, "targetType은 필수다");
        Objects.requireNonNull(targetId, "targetId는 필수다");
        if (targetId.isBlank()) {
            throw new IllegalArgumentException("targetId는 공백일 수 없다");
        }
    }
}
