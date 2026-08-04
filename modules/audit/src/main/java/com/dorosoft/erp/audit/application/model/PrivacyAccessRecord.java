package com.dorosoft.erp.audit.application.model;

import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PrivacyAccessRecord(
        UUID accessLogId,
        PrivacyAccessCommand command,
        PrivacyAccessContext context,
        Instant retentionUntil,
        Instant recordedAt
) {
    public PrivacyAccessRecord {
        Objects.requireNonNull(accessLogId);
        Objects.requireNonNull(command);
        Objects.requireNonNull(context);
        Objects.requireNonNull(retentionUntil);
        Objects.requireNonNull(recordedAt);
    }
}
