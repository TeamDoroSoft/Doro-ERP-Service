package com.dorosoft.erp.identity.application.account;

import com.dorosoft.erp.audit.application.api.AuditContext;
import com.dorosoft.erp.audit.application.api.AuditRecordCommand;
import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.audit.application.api.PrivacyAccessCommand;
import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.audit.application.api.PrivacyAccessLogger;
import com.dorosoft.erp.audit.application.api.PrivacyAccessSubject;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The only Identity adapter to Feature 17 write APIs. */
@Component
public final class IdentityAuditRecorder {
    private final AuditWriter auditWriter;
    private final PrivacyAccessLogger privacyAccessLogger;

    public IdentityAuditRecorder(AuditWriter auditWriter, PrivacyAccessLogger privacyAccessLogger) {
        this.auditWriter = Objects.requireNonNull(auditWriter);
        this.privacyAccessLogger = Objects.requireNonNull(privacyAccessLogger);
    }

    public void accountCreated(
            IdentityOperationContext context, String actorDisplayName, UUID accountId, UUID roleId,
            String status, String roleCode, List<String> permissionCodes) {
        write(context, actorDisplayName, AuditRecordCommand.employeeAccountCreated(
                UUID.randomUUID(), accountId, roleId,
                Map.of("status", status, "role", roleCode, "permissionCodes", permissionCodes)));
    }

    public void accountStatusChanged(
            IdentityOperationContext context, String actorDisplayName, UUID accountId,
            String beforeStatus, String afterStatus) {
        Map<String, Object> before = Map.of("status", beforeStatus);
        Map<String, Object> after = Map.of("status", afterStatus);
        AuditRecordCommand command = "ACTIVE".equals(afterStatus)
                ? AuditRecordCommand.employeeAccountActivated(UUID.randomUUID(), accountId, before, after)
                : AuditRecordCommand.employeeAccountDeactivated(UUID.randomUUID(), accountId, before, after);
        write(context, actorDisplayName, command);
    }

    public void accountUnlocked(
            IdentityOperationContext context, String actorDisplayName, UUID accountId,
            Map<String, Object> before, Map<String, Object> after, String reason) {
        write(context, actorDisplayName, AuditRecordCommand.accountLoginUnlocked(
                UUID.randomUUID(), accountId, before, after, reason));
    }

    public void employeeRoleChanged(
            IdentityOperationContext context, String actorDisplayName, UUID accountId,
            UUID beforeRoleId, UUID afterRoleId,
            Map<String, Object> before, Map<String, Object> after) {
        write(context, actorDisplayName, AuditRecordCommand.employeePermissionsChanged(
                UUID.randomUUID(), accountId, beforeRoleId, afterRoleId, before, after));
    }

    public void rolePermissionsChanged(
            IdentityOperationContext context, String actorDisplayName, UUID roleId,
            Map<String, Object> before, Map<String, Object> after) {
        write(context, actorDisplayName, AuditRecordCommand.rolePermissionsChanged(
                UUID.randomUUID(), roleId, before, after));
    }

    private void write(
            IdentityOperationContext context, String actorDisplayName, AuditRecordCommand command) {
        auditWriter.record(command, AuditContext.identityUser(
                context.tenantId(), context.actorAccountId(), context.actorRoleCode(),
                actorDisplayName, context.requestId(), context.occurredAt()));
    }

    public void privacyCreate(IdentityOperationContext context, boolean success, List<UUID> subjectIds) {
        privacy(context, PrivacyKind.CREATE, success, subjectIds);
    }

    public void privacyRead(IdentityOperationContext context, boolean success, List<UUID> subjectIds) {
        privacy(context, PrivacyKind.READ, success, subjectIds);
    }

    public void privacyUpdate(IdentityOperationContext context, boolean success, List<UUID> subjectIds) {
        privacy(context, PrivacyKind.UPDATE, success, subjectIds);
    }

    private void privacy(
            IdentityOperationContext context, PrivacyKind kind,
            boolean success, List<UUID> subjectIds) {
        List<PrivacyAccessSubject> subjects = subjectIds.stream().distinct()
                .map(id -> new PrivacyAccessSubject("EMPLOYEE", id))
                .toList();
        try {
            PrivacyAccessCommand command = switch (kind) {
                case CREATE -> success
                        ? PrivacyAccessCommand.employeeAccountCreate(subjects)
                        : PrivacyAccessCommand.employeeAccountCreateDenied(subjects);
                case READ -> success
                        ? PrivacyAccessCommand.employeeAccountRead(subjects)
                        : PrivacyAccessCommand.employeeAccountReadDenied(subjects);
                case UPDATE -> success
                        ? PrivacyAccessCommand.employeeAccountUpdate(subjects)
                        : PrivacyAccessCommand.employeeAccountUpdateDenied(subjects);
            };
            var result = privacyAccessLogger.append(command,
                    new PrivacyAccessContext(
                            context.tenantId(), actorTypeName(context.actorRoleCode()),
                            context.actorAccountId(), context.actorRoleCode(), context.requestId(),
                            context.clientAddressCiphertext(), context.clientAddressKeyVersion(), context.occurredAt()));
            if (result == null || !result.accepted()) {
                throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE);
            }
        } catch (IdentityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdentityException(IdentityErrorCode.PRIVACY_ACCESS_LOG_UNAVAILABLE, exception);
        }
    }

    private static String actorTypeName(String roleCode) {
        return "ADMIN".equals(roleCode) ? "ADMIN" : "EMPLOYEE";
    }

    private enum PrivacyKind {
        CREATE,
        READ,
        UPDATE
    }
}
