package com.dorosoft.erp.audit.application.api;

import java.time.Instant;
import java.util.UUID;

public record PrivacyAccessResult(
        boolean accepted,
        UUID accessLogId,
        Instant recordedAt
) {
    public static PrivacyAccessResult success(UUID accessLogId, Instant recordedAt) {
        return new PrivacyAccessResult(true, accessLogId, recordedAt);
    }
}
