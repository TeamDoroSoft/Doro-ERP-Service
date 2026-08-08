package com.dorosoft.erp.storeaccess.presentation.identity;

import jakarta.validation.constraints.NotBlank;

public record ReauthenticateRequest(@NotBlank String password) {
}
