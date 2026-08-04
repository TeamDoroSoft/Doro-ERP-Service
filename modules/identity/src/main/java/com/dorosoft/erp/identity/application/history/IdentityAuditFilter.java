package com.dorosoft.erp.identity.application.history;

import java.time.Instant;
import java.util.UUID;

public record IdentityAuditFilter(
        String eventType,
        UUID actorAccountId,
        UUID targetAccountId,
        Instant from,
        Instant to,
        String cursor,
        int size
) {
}
