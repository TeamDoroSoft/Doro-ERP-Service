package com.dorosoft.erp.storeaccess.presentation.identity;

import jakarta.validation.constraints.NotBlank;

public record RegisterKioskDeviceRequest(@NotBlank String deviceCode) {
}
