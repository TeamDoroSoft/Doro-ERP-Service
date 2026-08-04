package com.dorosoft.erp.identity.presentation.authentication;

import com.dorosoft.erp.identity.application.authentication.LoginResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoginResponse(
        UUID accountId,
        String displayName,
        String roleCode,
        List<String> permissions,
        boolean mustChangePassword,
        String csrfToken,
        int maxInactiveIntervalSeconds,
        Instant absoluteExpiresAt
) {
    static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accountId(), result.displayName(), result.roleCode(),
                result.permissions().stream().sorted().toList(), result.mustChangePassword(),
                result.csrfToken(), result.maxInactiveIntervalSeconds(), result.absoluteExpiresAt());
    }

    @Override
    public String toString() {
        return "LoginResponse[accountId=%s, roleCode=%s, csrfToken=redacted]"
                .formatted(accountId, roleCode);
    }
}
