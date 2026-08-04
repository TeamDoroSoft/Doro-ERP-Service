package com.dorosoft.erp.audit.application.model;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record AuditRecord(
        UUID auditId,
        AuditRecordCommand command,
        AuditContext context,
        String beforeJson,
        String afterJson,
        byte[] payloadHmac,
        Instant retentionUntil,
        Instant recordedAt
) {
    public AuditRecord {
        Objects.requireNonNull(auditId);
        Objects.requireNonNull(command);
        Objects.requireNonNull(context);
        Objects.requireNonNull(beforeJson);
        Objects.requireNonNull(afterJson);
        payloadHmac = Arrays.copyOf(Objects.requireNonNull(payloadHmac), payloadHmac.length);
        Objects.requireNonNull(retentionUntil);
        Objects.requireNonNull(recordedAt);
    }

    @Override
    public byte[] payloadHmac() {
        return Arrays.copyOf(payloadHmac, payloadHmac.length);
    }
}
