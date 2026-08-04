package com.dorosoft.erp.identity.application.history;

import java.util.UUID;

public record IdentityAuditActor(
        UUID accountId,
        String roleCode,
        String displayNameSnapshot
) {
}
