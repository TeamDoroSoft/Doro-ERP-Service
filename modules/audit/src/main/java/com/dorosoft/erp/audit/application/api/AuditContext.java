package com.dorosoft.erp.audit.application.api;

import com.dorosoft.erp.audit.domain.ActorType;

import java.time.Instant;
import java.util.UUID;

public record AuditContext(
        String tenantId,
        ActorType actorType,
        UUID actorId,
        String actorRoleSnapshot,
        String actorDisplayNameSnapshot,
        String requestId,
        Instant occurredAt
) {

    /**
     * Fixed Feature 01 context factory. The caller supplies only JDK values while
     * the Audit public API owns the ADMIN/EMPLOYEE actor classification.
     */
    public static AuditContext identityUser(
            String tenantId,
            UUID actorId,
            String actorRoleCode,
            String actorDisplayName,
            String requestId,
            Instant occurredAt
    ) {
        ActorType actorType = "ADMIN".equals(actorRoleCode) ? ActorType.ADMIN : ActorType.EMPLOYEE;
        return new AuditContext(
                tenantId,
                actorType,
                actorId,
                actorRoleCode,
                actorDisplayName,
                requestId,
                occurredAt
        );
    }

    public static AuditContext storeUser(
            String tenantId,
            UUID actorId,
            String actorRoleCode,
            String actorDisplayName,
            String requestId,
            Instant occurredAt
    ) {
        ActorType actorType = "ADMIN".equals(actorRoleCode) ? ActorType.ADMIN : ActorType.EMPLOYEE;
        return new AuditContext(
                tenantId,
                actorType,
                actorId,
                actorRoleCode,
                actorDisplayName,
                requestId,
                occurredAt
        );
    }
}
