package com.dorosoft.erp.audit.application.port;

import com.dorosoft.erp.audit.domain.record.AuditRecord;

public interface AuditRecordRepositoryPort {

    SaveOutcome save(AuditRecord record);
}
