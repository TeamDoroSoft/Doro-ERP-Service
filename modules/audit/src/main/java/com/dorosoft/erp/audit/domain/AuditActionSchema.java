package com.dorosoft.erp.audit.domain;

import java.util.Set;

public record AuditActionSchema(
        AuditDomain domain,
        AuditTargetType primaryTargetType,
        Set<String> allowedFields,
        Set<String> requiredBeforeFields,
        Set<String> requiredAfterFields,
        boolean reasonRequired,
        boolean reasonCodeRequired
) {
}
