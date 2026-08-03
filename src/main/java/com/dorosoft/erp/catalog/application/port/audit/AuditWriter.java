package com.dorosoft.erp.catalog.application.port.audit;

/**
 * 감사 기록 계약. 공개 HTTP Write API는 없고 생산 모듈이 자신의 Transaction 안에서 호출한다.
 * Context는 호출자가 조립하지 않고 서버가 확정해 전달한다.
 */
public interface AuditWriter {

    AuditWriteResult record(AuditRecordCommand command, AuditContext context);
}
