package com.dorosoft.erp.identity.application.history;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IdentityAuditAccessContext(
        String tenantId,
        UUID accessorAccountId,
        String accessorRoleCode,
        String requestId,
        String clientAddressCiphertext,
        String clientAddressKeyVersion,
        Instant accessedAt
) {
    public IdentityAuditAccessContext {
        if (tenantId == null || tenantId.isBlank() || accessorAccountId == null
                || accessorRoleCode == null || accessorRoleCode.isBlank()
                || requestId == null || requestId.isBlank()
                || clientAddressCiphertext == null || clientAddressCiphertext.isBlank()
                || clientAddressKeyVersion == null || clientAddressKeyVersion.isBlank()) {
            throw new IllegalArgumentException("Verified identity audit access context is incomplete");
        }
        Objects.requireNonNull(accessedAt);
    }
}
