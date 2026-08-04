package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress;
import java.util.Objects;

public record LoginCommand(
        String loginId,
        String password,
        ClientIpAddress clientIpAddress,
        String trustedClientAddress,
        String requestId
) {
    public LoginCommand {
        Objects.requireNonNull(loginId, "loginId");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(clientIpAddress, "clientIpAddress");
        requireText(trustedClientAddress, "trustedClientAddress");
        requireText(requestId, "requestId");
    }

    @Override
    public String toString() {
        return "LoginCommand[credentials=redacted]";
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
