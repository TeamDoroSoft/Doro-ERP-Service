package com.dorosoft.erp.audit.application.api;

import java.time.Instant;
import java.util.UUID;

public record PrivacyAccessContext(
        String tenantId,
        String accessorType,
        UUID accessorId,
        String accessorRoleSnapshot,
        String requestId,
        String clientAddressCiphertext,
        String clientAddressKeyVersion,
        Instant accessedAt
) {
}
