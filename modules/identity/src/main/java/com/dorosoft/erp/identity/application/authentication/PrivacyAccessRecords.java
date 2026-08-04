package com.dorosoft.erp.identity.application.authentication;

import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.api.PrivacyAccessSubject;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import java.util.List;
import java.util.UUID;

final class PrivacyAccessRecords {
    private PrivacyAccessRecords() {
    }

    static void appendEmployeeRead(
            PrivacyAccessLogger logger,
            UUID accountId,
        PrivacyAccessContext context
    ) {
        try {
            var result = logger.append(
                    PrivacyAccessCommand.employeeAccountRead(
                            List.of(new PrivacyAccessSubject("EMPLOYEE", accountId))),
                    context
            );
            if (result == null || !result.accepted()) {
                throw new IllegalStateException("Privacy access record was not accepted");
            }
        } catch (RuntimeException exception) {
            throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, exception);
        }
    }

    static void appendDenied(
            PrivacyAccessLogger logger,
            DeniedPrivacyOperation operation,
            PrivacyAccessContext context
    ) {
        try {
            PrivacyAccessCommand command = switch (operation) {
                case EMPLOYEE_ACCOUNT_READ -> PrivacyAccessCommand.employeeAccountReadDenied(List.of());
                case EMPLOYEE_ACCOUNT_CREATE -> PrivacyAccessCommand.employeeAccountCreateDenied(List.of());
                case EMPLOYEE_ACCOUNT_UPDATE -> PrivacyAccessCommand.employeeAccountUpdateDenied(List.of());
                case IDENTITY_AUDIT_EVENT_READ -> PrivacyAccessCommand.identityAuditEventReadDenied(List.of());
            };
            var result = logger.append(command, context);
            if (result == null || !result.accepted()) {
                throw new IllegalStateException("Privacy access denial record was not accepted");
            }
        } catch (RuntimeException exception) {
            throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, exception);
        }
    }
}
