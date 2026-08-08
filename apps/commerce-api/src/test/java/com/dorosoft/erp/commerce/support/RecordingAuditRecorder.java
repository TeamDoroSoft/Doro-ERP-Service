package com.dorosoft.erp.commerce.support;

import com.dorosoft.erp.commerce.application.api.audit.AuditRecord;
import com.dorosoft.erp.commerce.application.api.audit.CatalogAuditAction;
import com.dorosoft.erp.commerce.application.port.audit.AuditRecorderPort;
import java.util.ArrayList;
import java.util.List;

public final class RecordingAuditRecorder implements AuditRecorderPort {

    private final List<AuditRecord> records = new ArrayList<>();

    @Override
    public void record(AuditRecord auditRecord) {
        records.add(auditRecord);
    }

    public List<AuditRecord> records() {
        return List.copyOf(records);
    }

    public List<CatalogAuditAction> actions() {
        return records.stream().map(AuditRecord::action).toList();
    }

    public void clear() {
        records.clear();
    }
}
