package com.dorosoft.erp.identity.application.history;

import java.util.List;

public record IdentityAuditQueryResult(
        List<IdentityAuditEventItem> items,
        String nextCursor
) {
    public IdentityAuditQueryResult {
        items = List.copyOf(items);
    }
}
