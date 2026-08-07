package com.dorosoft.erp.audit.domain.record;

import java.util.Objects;
import java.util.UUID;

public record AuditActor(String type, UUID id, String role) {

    public AuditActor {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }
}
