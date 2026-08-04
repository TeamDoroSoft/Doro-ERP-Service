package com.dorosoft.erp.audit.application.api;

import java.util.List;

public record AuditQueryResult(
        List<AuditRecordProjection> items,
        String nextCursor
) {
}
