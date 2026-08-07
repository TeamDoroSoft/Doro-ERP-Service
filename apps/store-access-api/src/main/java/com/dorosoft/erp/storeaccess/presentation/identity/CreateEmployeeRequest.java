package com.dorosoft.erp.storeaccess.presentation.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateEmployeeRequest(
        @NotBlank String loginId,
        @NotBlank String temporaryPassword,
        @NotBlank @Pattern(regexp = "OWNER|MANAGER|STAFF") String role) {
}
