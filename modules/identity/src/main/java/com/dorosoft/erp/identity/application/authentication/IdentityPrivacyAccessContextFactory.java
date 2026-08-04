package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import java.time.Instant;
import java.util.UUID;

/** Encrypts the trusted client address and builds a verified Feature 17 access context. */
public interface IdentityPrivacyAccessContextFactory {
    PrivacyAccessContext create(
            String tenantId,
            UUID accessorAccountId,
            String accessorRoleCode,
            String requestId,
            String trustedClientAddress,
            Instant accessedAt
    );
}
