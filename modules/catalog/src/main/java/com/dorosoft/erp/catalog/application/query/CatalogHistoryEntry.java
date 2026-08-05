package com.dorosoft.erp.catalog.application.query;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** GET /catalog/history 한 줄. 5.17 감사 기록을 Catalog 응답 형태로 옮긴다. */
public record CatalogHistoryEntry(
        UUID auditId,
        String action,
        String actorType,
        UUID actorId,
        String actorRoleSnapshot,
        String targetType,
        UUID targetId,
        Instant occurredAt,
        String requestId,
        Map<String, Object> beforeValue,
        Map<String, Object> afterValue) {}
