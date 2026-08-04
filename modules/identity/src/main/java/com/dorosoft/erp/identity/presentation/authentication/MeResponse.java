package com.dorosoft.erp.identity.presentation.authentication;

import com.dorosoft.erp.identity.application.authentication.MeResult;
import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID accountId,
        String displayName,
        String status,
        String lockStatus,
        boolean mustChangePassword,
        String roleCode,
        List<String> permissions
) {
    static MeResponse from(MeResult result) {
        return new MeResponse(
                result.accountId(), result.displayName(), result.status(), result.lockStatus(),
                result.mustChangePassword(), result.roleCode(), result.permissions().stream().sorted().toList());
    }
}
