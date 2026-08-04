package com.dorosoft.erp.identity.application.history;

public enum IdentityAuditSourceType {
    IDENTITY_SECURITY_EVENT(0),
    AUDIT_RECORD(1);

    private final int sourceOrder;

    IdentityAuditSourceType(int sourceOrder) {
        this.sourceOrder = sourceOrder;
    }

    public int sourceOrder() {
        return sourceOrder;
    }
}
