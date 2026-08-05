package com.dorosoft.erp.catalog.application.query;

import java.time.Instant;
import java.util.UUID;

/** GET /catalog/history 조회 조건. targetType은 "CATEGORY"·"PRODUCT"만 의미가 있다. */
public record CatalogHistoryFilter(
        String targetType,
        UUID targetId,
        String action,
        UUID actorId,
        Instant from,
        Instant to,
        String cursor,
        int limit) {}
