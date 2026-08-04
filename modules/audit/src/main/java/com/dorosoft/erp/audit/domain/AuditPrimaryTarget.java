package com.dorosoft.erp.audit.domain;

import java.util.UUID;

public record AuditPrimaryTarget(
        AuditTargetType targetType,
        UUID targetId
) {
}
