package com.dorosoft.erp.audit.application.api;

import java.util.List;
import java.util.Objects;

/**
 * 생산 모듈이 자신의 Transaction 안에서 전달하는 감사 기록 Command.
 * actor·occurredAt 등 서버 확정 값은 여기 담지 않고 AuditContext가 담는다.
 */
public record AuditRecordCommand(
        String domain,
        String action,
        String operationId,
        int eventSequence,
        AuditTarget primaryTarget,
        List<AuditRelatedTarget> relatedTargets,
        String beforeValue,
        String afterValue,
        String reasonCode,
        String reason,
        String valueSchemaVersion) {

    /** 생성 전·물리 삭제 후의 값 표현. */
    private static final String EMPTY_JSON_OBJECT = "{}";

    private static final int REASON_MAX_LENGTH = 500;

    public AuditRecordCommand {
        domain = requireText(domain, "domain");
        action = requireText(action, "action");
        operationId = requireText(operationId, "operationId");
        valueSchemaVersion = requireText(valueSchemaVersion, "valueSchemaVersion");
        Objects.requireNonNull(primaryTarget, "primaryTarget은 필수다");

        if (eventSequence < 0) {
            throw new IllegalArgumentException("eventSequence는 0 이상이어야 한다");
        }

        relatedTargets = relatedTargets == null ? List.of() : List.copyOf(relatedTargets);

        beforeValue = beforeValue == null ? EMPTY_JSON_OBJECT : beforeValue;
        afterValue = afterValue == null ? EMPTY_JSON_OBJECT : afterValue;

        if (reason != null && (reason.isBlank() || reason.length() > REASON_MAX_LENGTH)) {
            throw new IllegalArgumentException("reason은 1~500자여야 한다");
        }
    }

    /** 관련 대상·사유가 없는 단순 Action용 편의 팩터리. */
    public static AuditRecordCommand of(
            String domain,
            String action,
            String operationId,
            int eventSequence,
            AuditTarget primaryTarget,
            String beforeValue,
            String afterValue,
            String valueSchemaVersion) {
        return new AuditRecordCommand(
                domain,
                action,
                operationId,
                eventSequence,
                primaryTarget,
                List.of(),
                beforeValue,
                afterValue,
                null,
                null,
                valueSchemaVersion);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "은(는) 필수다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 공백일 수 없다");
        }
        return value;
    }
}
