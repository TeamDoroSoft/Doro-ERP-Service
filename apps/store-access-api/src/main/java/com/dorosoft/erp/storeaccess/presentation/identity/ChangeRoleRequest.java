package com.dorosoft.erp.storeaccess.presentation.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequest(@NotBlank @Pattern(regexp = "OWNER|MANAGER|STAFF") String role) {
}
