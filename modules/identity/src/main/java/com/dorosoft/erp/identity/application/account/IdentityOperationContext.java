package com.dorosoft.erp.identity.application.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Verified request metadata needed by Identity audit and privacy writes. */
public record IdentityOperationContext(
        String tenantId,
        UUID actorAccountId,
        String actorRoleCode,
        String requestId,
        String clientAddressCiphertext,
        String clientAddressKeyVersion,
        Instant occurredAt
) {
    public IdentityOperationContext {
        tenantId = requireText(tenantId, "tenantId");
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        actorRoleCode = requireText(actorRoleCode, "actorRoleCode");
        requestId = requireText(requestId, "requestId");
        clientAddressCiphertext = requireText(clientAddressCiphertext, "clientAddressCiphertext");
        clientAddressKeyVersion = requireText(clientAddressKeyVersion, "clientAddressKeyVersion");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
