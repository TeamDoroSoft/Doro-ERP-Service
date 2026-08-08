package com.dorosoft.erp.storeaccess.presentation.identity;

import java.util.UUID;

/** ADR-02-009: "성공 응답에는 계정 ID, 상태와 passwordChangeRequired만 포함한다" — never a temporary password. */
public record EmployeeIdentityResponse(UUID employeeId, String role, String status, boolean passwordChangeRequired) {
}
