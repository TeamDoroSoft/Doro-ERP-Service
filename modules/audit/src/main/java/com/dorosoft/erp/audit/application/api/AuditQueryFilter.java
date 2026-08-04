package com.dorosoft.erp.audit.application.api;

import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.domain.AuditTargetType;

import java.time.Instant;
import java.util.UUID;

public record AuditQueryFilter(
        AuditDomain domain,
        String action,
        UUID actorId,
        AuditTargetType targetType,
        UUID targetId,
        Instant from,
        Instant to,
        int limit,
        String cursor
) {
    /** Identity-owned composite history query without leaking Audit domain registry types. */
    public static AuditQueryFilter identity(
            String action,
            UUID actorAccountId,
            UUID targetAccountId,
            Instant from,
            Instant to,
            int limit,
            String cursor
    ) {
        return new AuditQueryFilter(
                AuditDomain.IDENTITY,
                action,
                actorAccountId,
                targetAccountId == null ? null : AuditTargetType.ACCOUNT,
                targetAccountId,
                from,
                to,
                limit,
                cursor
        );
    }
}
