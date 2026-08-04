package com.dorosoft.erp.identity.application.history;

public interface IdentityAuditHistoryQuery {
    IdentityAuditQueryResult query(IdentityAuditFilter filter, IdentityAuditAccessContext accessContext);
}
