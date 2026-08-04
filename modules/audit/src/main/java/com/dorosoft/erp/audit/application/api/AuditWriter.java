package com.dorosoft.erp.audit.application.api;

import com.dorosoft.erp.audit.domain.AuditWriteResult;

public interface AuditWriter {

    AuditWriteResult record(AuditRecordCommand command, AuditContext context);
}
