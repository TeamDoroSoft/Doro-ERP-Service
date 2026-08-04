package com.dorosoft.erp.identity.application.history;

public interface IdentityAuditCursorCodec {
    String encode(String tenantId, NormalizedIdentityAuditFilter filter, IdentityAuditSortKey lastKey);

    DecodedCursor decode(String tenantId, NormalizedIdentityAuditFilter requestedFilter, String cursor);

    record DecodedCursor(NormalizedIdentityAuditFilter filter, IdentityAuditSortKey lastKey) {
    }
}
