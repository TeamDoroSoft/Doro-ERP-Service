package com.dorosoft.erp.audit.domain.record;

import java.util.Objects;
import java.util.UUID;

public record AuditTarget(String type, UUID id) {

    public AuditTarget {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }
}
