package com.dorosoft.erp.identity.presentation.authentication;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {
    @Override
    public String toString() {
        return "LoginRequest[credentials=redacted]";
    }
}
