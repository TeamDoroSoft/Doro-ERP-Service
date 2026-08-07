package com.dorosoft.erp.storeaccess.presentation.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeStatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
}
