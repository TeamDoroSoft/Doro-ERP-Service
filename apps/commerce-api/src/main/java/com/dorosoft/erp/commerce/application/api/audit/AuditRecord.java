package com.dorosoft.erp.commerce.application.api.audit;

import com.dorosoft.erp.commerce.application.api.security.ActorContext;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Outbox에 남길 최소 Allowlist Record.
 *
 * <p>{@code metadata}에는 Secret·개인정보·원문 Idempotency Key를 넣지 않는다.
 */
public record AuditRecord(
        ActorContext actor,
        CatalogAuditAction action,
        String targetType,
        UUID targetId,
        Map<String, Object> metadata) {

    public AuditRecord {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
