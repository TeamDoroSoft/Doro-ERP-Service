package com.dorosoft.erp.commerce.application.port.audit;

import com.dorosoft.erp.commerce.application.api.audit.AuditRecord;

/**
 * 업무 Use Case가 호출하는 서비스 내부 Audit Recorder.
 *
 * <p>Adapter는 중앙 Audit Service를 동기 호출하지 않고 같은 Local Transaction의 Outbox에 기록한다.
 */
public interface AuditRecorderPort {

    void record(AuditRecord auditRecord);
}
