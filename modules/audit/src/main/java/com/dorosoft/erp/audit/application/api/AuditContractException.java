package com.dorosoft.erp.audit.application.api;

public final class AuditContractException extends RuntimeException {
    private final AuditErrorCode code;

    public AuditContractException(AuditErrorCode code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public AuditContractException(AuditErrorCode code, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
    }

    public AuditErrorCode code() {
        return code;
    }
}
