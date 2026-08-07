package com.dorosoft.erp.audit.application.api.ingest;

import com.dorosoft.erp.platform.messaging.audit.AuditEventEnvelope;

public interface IngestAuditEventUseCase {

    IngestOutcome handle(AuditEventEnvelope event);
}
