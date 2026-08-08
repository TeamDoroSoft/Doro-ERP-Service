package com.dorosoft.erp.storeaccess.presentation.identity;

import jakarta.validation.constraints.NotBlank;

public record KioskActivateRequest(
        @NotBlank String tenantCode, @NotBlank String deviceCode, @NotBlank String secret) {
}
