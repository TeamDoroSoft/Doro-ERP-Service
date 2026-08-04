package com.dorosoft.erp.audit.application.usecase;

public final class AuditOperationAlreadyExistsException extends RuntimeException {
    public AuditOperationAlreadyExistsException(Throwable cause) {
        super(cause);
    }
}
