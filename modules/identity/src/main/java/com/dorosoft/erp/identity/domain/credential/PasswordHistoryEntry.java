package com.dorosoft.erp.identity.domain.credential;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 현재 Hash를 제외한 과거 비밀번호 Hash 한 건. */
public record PasswordHistoryEntry(
        UUID passwordHistoryId,
        UUID employeeAccountId,
        String passwordHash,
        Instant changedAt
) {
    public PasswordHistoryEntry {
        Objects.requireNonNull(passwordHistoryId, "passwordHistoryId");
        Objects.requireNonNull(employeeAccountId, "employeeAccountId");
        PasswordPolicy.validateEncodedHash(passwordHash);
        Objects.requireNonNull(changedAt, "changedAt");
    }
}
