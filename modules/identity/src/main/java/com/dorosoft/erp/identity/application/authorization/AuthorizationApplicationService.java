package com.dorosoft.erp.identity.application.authorization;

import com.dorosoft.erp.identity.application.account.IdentityAuditRecorder;
import com.dorosoft.erp.identity.application.account.IdentityOperationContext;
import com.dorosoft.erp.identity.application.account.IfMatchVersion;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class AuthorizationApplicationService {
    private final AuthorizationTransactions transactions;
    private final IdentityAuditRecorder recorder;

    public AuthorizationApplicationService(
            AuthorizationTransactions transactions,
            IdentityAuditRecorder recorder
    ) {
        this.transactions = transactions;
        this.recorder = recorder;
    }

    public List<RoleResult> listRoles() {
        return transactions.listRoles();
    }

    public List<PermissionResult> listPermissions() {
        return transactions.listPermissions();
    }

    public RolePermissionsResult replaceRolePermissions(
            String roleCode,
            String ifMatch,
            ReplaceRolePermissionsCommand command,
            IdentityOperationContext context
    ) {
        return transactions.replaceRolePermissions(
                roleCode, IfMatchVersion.parse(ifMatch), command, context);
    }

    public EmployeeRoleResult replaceEmployeeRole(
            UUID accountId,
            String ifMatch,
            ReplaceEmployeeRoleCommand command,
            IdentityOperationContext context
    ) {
        final long expectedVersion;
        try {
            expectedVersion = IfMatchVersion.parse(ifMatch);
        } catch (IdentityException exception) {
            recorder.privacyUpdate(context, false, List.of());
            throw exception;
        }
        try {
            return transactions.replaceEmployeeRole(
                    accountId, expectedVersion, command, context);
        } catch (IdentityException exception) {
            if (exception.code() == IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE) {
                throw exception;
            }
            List<UUID> subjects = exception.code() == IdentityErrorCode.ACCOUNT_NOT_FOUND
                    ? List.of() : List.of(accountId);
            recorder.privacyUpdate(context, false, subjects);
            throw exception;
        } catch (IllegalArgumentException exception) {
            recorder.privacyUpdate(context, false, List.of(accountId));
            throw new IdentityException(IdentityErrorCode.VALIDATION_FAILED, exception);
        }
    }

    public record ReplaceRolePermissionsCommand(List<String> permissionCodes) {
    }

    public record ReplaceEmployeeRoleCommand(String roleCode) {
    }

    public record RoleResult(
            String code, String name, List<String> permissionCodes, long version) {
        public RoleResult {
            permissionCodes = List.copyOf(permissionCodes);
        }
    }

    public record PermissionResult(String code, String description) {
    }

    public record RolePermissionsResult(
            String roleCode,
            List<String> permissionCodes,
            long version,
            int affectedAccountCount,
            boolean reauthenticationRequired
    ) {
        public RolePermissionsResult {
            permissionCodes = List.copyOf(permissionCodes);
        }
    }

    public record EmployeeRoleResult(
            UUID accountId,
            String roleCode,
            List<String> permissionCodes,
            long version,
            boolean reauthenticationRequired
    ) {
        public EmployeeRoleResult {
            permissionCodes = List.copyOf(permissionCodes);
        }
    }
}
