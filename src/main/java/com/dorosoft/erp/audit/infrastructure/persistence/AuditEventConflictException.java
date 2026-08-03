package com.dorosoft.erp.audit.infrastructure.persistence;

public class AuditEventConflictException extends RuntimeException {
    public AuditEventConflictException(String domain, String operationId, int eventSequence) {
        super("동일한 감사 이벤트 키에 다른 payload가 이미 기록되어 있다: "
                + domain + "/" + operationId + "/" + eventSequence);
    }
}
