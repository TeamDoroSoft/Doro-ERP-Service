package com.dorosoft.erp.storeaccess.presentation.identity;

import java.time.Instant;
import java.util.UUID;

public record EmployeeResponse(
        UUID id, String loginId, String role, String status, boolean passwordChangeRequired, Instant createdAt) {
}
