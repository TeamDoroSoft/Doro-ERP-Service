package com.dorosoft.erp.storeaccess.presentation.identity;

import java.util.UUID;

public record LoginResponse(UUID employeeId, String role, boolean passwordChangeRequired) {
}
