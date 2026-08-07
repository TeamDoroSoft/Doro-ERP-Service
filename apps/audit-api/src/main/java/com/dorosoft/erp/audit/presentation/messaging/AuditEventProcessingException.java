package com.dorosoft.erp.audit.presentation.messaging;

public class AuditEventProcessingException extends RuntimeException {

    public AuditEventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
