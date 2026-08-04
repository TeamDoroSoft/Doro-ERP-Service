package com.dorosoft.erp.audit.domain;

import java.util.UUID;

public record AuditRelatedTarget(
        AuditRelationType relationType,
        AuditTargetType targetType,
        UUID targetId
) {
}
