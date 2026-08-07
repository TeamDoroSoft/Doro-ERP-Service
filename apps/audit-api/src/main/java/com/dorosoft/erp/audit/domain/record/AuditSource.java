package com.dorosoft.erp.audit.domain.record;

import java.util.Objects;
import java.util.UUID;

public record AuditSource(String service, UUID eventId) {

    public AuditSource {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
    }
}
