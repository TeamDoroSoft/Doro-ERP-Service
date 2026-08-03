package com.dorosoft.erp.audit.application.api;

import java.time.Instant;
import java.util.Objects;

/**
 * 서버가 확정하는 감사 실행 맥락.
 * 호출자가 임의 조립하지 않고 서버(인증 주체·요청 컨텍스트)가 확정해 주입한다.
 * 따라서 원시 값으로 조립하는 편의 팩터리를 두지 않는다.
 */
public record AuditContext(
        String actor,
        String actorRole,
        Instant occurredAt,
        String requestId) {

    public AuditContext {
        actor = requireText(actor, "actor");
        actorRole = requireText(actorRole, "actorRole");
        Objects.requireNonNull(occurredAt, "occurredAt은 필수다");
        requestId = requireText(requestId, "requestId");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "은(는) 필수다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 공백일 수 없다");
        }
        return value;
    }
}
