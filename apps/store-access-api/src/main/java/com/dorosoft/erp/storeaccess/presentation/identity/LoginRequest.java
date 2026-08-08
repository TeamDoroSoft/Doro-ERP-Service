package com.dorosoft.erp.storeaccess.presentation.identity;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String tenantCode,
        @NotBlank String loginId,
        @NotBlank String password) {
}
