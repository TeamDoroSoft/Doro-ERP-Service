package com.dorosoft.erp.storeaccess.presentation.identity;

import jakarta.validation.constraints.NotBlank;

public record ChangeOwnPasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
}
