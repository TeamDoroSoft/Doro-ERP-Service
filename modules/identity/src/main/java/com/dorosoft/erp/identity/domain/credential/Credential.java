package com.dorosoft.erp.identity.domain.credential;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 계정과 일대일인 현재 자격 증명. */
public record Credential(
        UUID employeeAccountId,
        String passwordHash,
        boolean mustChangePassword,
        long credentialVersion,
        Instant passwordChangedAt
) {
    public Credential {
        Objects.requireNonNull(employeeAccountId, "employeeAccountId");
        PasswordPolicy.validateEncodedHash(passwordHash);
        Objects.requireNonNull(passwordChangedAt, "passwordChangedAt");
        if (credentialVersion < 0) {
            throw new IllegalArgumentException("credentialVersion must be greater than or equal to 0");
        }
    }

    public static Credential initial(UUID accountId, String passwordHash, Instant changedAt) {
        return new Credential(accountId, passwordHash, true, 0, changedAt);
    }

    public Credential changePassword(String nextHash, Instant changedAt) {
        return new Credential(employeeAccountId, nextHash, false, credentialVersion, changedAt);
    }

    public Credential resetPassword(String nextHash, Instant changedAt) {
        return new Credential(employeeAccountId, nextHash, true, credentialVersion, changedAt);
    }

    /** 비용 상향 재해시는 History를 만들지 않지만 자격 증명 version은 증가한다. */
    public Credential rehashAfterSuccessfulLogin(String nextHash, Instant changedAt) {
        return new Credential(employeeAccountId, nextHash, mustChangePassword, credentialVersion, changedAt);
    }
}
