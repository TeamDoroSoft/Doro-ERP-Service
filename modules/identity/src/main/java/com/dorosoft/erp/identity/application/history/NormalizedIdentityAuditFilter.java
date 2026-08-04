package com.dorosoft.erp.identity.application.history;

import java.time.Instant;
import java.util.UUID;

public record NormalizedIdentityAuditFilter(
        String eventType,
        UUID actorAccountId,
        UUID targetAccountId,
        Instant from,
        Instant to,
        int size,
        EventTypeSource eventTypeSource
) {
    public enum EventTypeSource {
        ALL,
        SECURITY,
        AUDIT
    }
}
