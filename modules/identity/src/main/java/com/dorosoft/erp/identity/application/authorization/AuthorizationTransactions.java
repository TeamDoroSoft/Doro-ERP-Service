package com.dorosoft.erp.identity.application.authorization;

import com.dorosoft.erp.identity.application.account.IdentityAuditRecorder;
import com.dorosoft.erp.identity.application.account.IdentityOperationContext;
import com.dorosoft.erp.identity.application.account.IfMatchVersion;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.EmployeeRoleResult;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.PermissionResult;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.RolePermissionsResult;
import com.dorosoft.erp.identity.application.authorization.AuthorizationApplicationService.RoleResult;
import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import com.dorosoft.erp.identity.application.port.EmployeeAccountRepository;
import com.dorosoft.erp.identity.application.port.RoleRepository;
import com.dorosoft.erp.identity.application.session.SessionRevoker;
import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import com.dorosoft.erp.identity.domain.role.PermissionCatalog;
import com.dorosoft.erp.identity.domain.role.Role;
import com.dorosoft.erp.identity.domain.role.RoleAssignment;
import com.dorosoft.erp.identity.domain.role.RolePermissionPolicy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public final class AuthorizationTransactions {
    private final RoleRepository roles;
    private final EmployeeAccountRepository accounts;
    private final SessionRevoker sessionRevoker;
    private final IdentityAuditRecorder recorder;
    private final Clock clock;

    public AuthorizationTransactions(
            RoleRepository roles,
            EmployeeAccountRepository accounts,
            SessionRevoker sessionRevoker,
            IdentityAuditRecorder recorder,
            Clock clock
    ) {
        this.roles = Objects.requireNonNull(roles);
        this.accounts = Objects.requireNonNull(accounts);
        this.sessionRevoker = Objects.requireNonNull(sessionRevoker);
        this.recorder = Objects.requireNonNull(recorder);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public List<RoleResult> listRoles() {
        return roles.findActiveRoles().stream()
                .map(role -> new RoleResult(
                        role.code().value(), role.name(), sorted(role.permissionCodes()), role.version()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResult> listPermissions() {
        List<PermissionResult> results = roles.findPermissions().stream()
                .map(permission -> new PermissionResult(permission.code(), permission.description()))
                .toList();
        if (results.size() != PermissionCatalog.all().size()
                || !PermissionCatalog.all().equals(results.stream()
                        .map(PermissionResult::code).collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalStateException("persisted permission registry does not match the application catalog");
        }
        return results;
    }

    @Transactional
    public RolePermissionsResult replaceRolePermissions(
            String rawRoleCode,
            long expectedVersion,
            AuthorizationApplicationService.ReplaceRolePermissionsCommand command,
            IdentityOperationContext context
    ) {
        String roleCode = normalizeRoleCode(rawRoleCode);
        Set<String> replacements = permissionSet(command);
        Role before = roles.findByCodeForUpdate(roleCode)
                .filter(Role::active)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.INVALID_ROLE));
        IfMatchVersion.verify(expectedVersion, before.version());
        validatePermissions(roleCode, replacements);
        List<UUID> affected = roles.findAccountIdsByRoleId(before.roleId());
        for (UUID accountId : affected) {
            sessionRevoker.revokeAll(accountId);
        }
        Role after = roles.save(before.replacePermissions(replacements));
        List<String> beforeCodes = sorted(before.permissionCodes());
        List<String> afterCodes = sorted(after.permissionCodes());
        recorder.rolePermissionsChanged(context, actorDisplayName(context), before.roleId(),
                map("roleCode", roleCode, "permissionCodes", beforeCodes, "version", before.version()),
                map("roleCode", roleCode, "permissionCodes", afterCodes, "version", after.version(),
                        "affectedAccountCount", affected.size()));
        boolean reauthenticate = affected.contains(context.actorAccountId());
        return new RolePermissionsResult(
                roleCode, afterCodes, after.version(), affected.size(), reauthenticate);
    }

    @Transactional
    public EmployeeRoleResult replaceEmployeeRole(
            UUID accountId,
            long expectedVersion,
            AuthorizationApplicationService.ReplaceEmployeeRoleCommand command,
            IdentityOperationContext context
    ) {
        if (command == null) {
            throw new IllegalArgumentException("role command is required");
        }
        String nextCode = normalizeRoleCode(command.roleCode());
        EmployeeAccount account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
        IfMatchVersion.verify(expectedVersion, account.version());
        Role nextRole = roles.findByCode(nextCode)
                .filter(Role::active)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.INVALID_ROLE));
        RoleAssignment beforeAssignment = roles.findAssignment(accountId)
                .orElseThrow(() -> new IllegalStateException("employee role assignment is missing"));
        Role beforeRole = roles.findByCode(beforeAssignment.roleCode().value())
                .orElseThrow(() -> new IllegalStateException("assigned role is missing"));
        final RoleAssignment replacement;
        try {
            replacement = beforeAssignment.replaceRole(
                    nextRole.roleId(), nextRole.code(), context.actorAccountId(), clock.instant());
        } catch (IllegalStateException exception) {
            throw new IdentityException(IdentityErrorCode.REPRESENTATIVE_ROLE_IMMUTABLE, exception);
        }

        roles.saveAssignment(replacement);
        EmployeeAccount versioned = accounts.incrementVersion(accountId, account.version());
        sessionRevoker.revokeAll(accountId);
        recorder.employeeRoleChanged(
                context, actorDisplayName(context), accountId, beforeRole.roleId(), nextRole.roleId(),
                map("role", beforeRole.code().value(), "permissionCodes", sorted(beforeRole.permissionCodes())),
                map("role", nextRole.code().value(), "permissionCodes", sorted(nextRole.permissionCodes())));
        recorder.privacyUpdate(context, true, List.of(accountId));
        return new EmployeeRoleResult(
                accountId, nextRole.code().value(), sorted(nextRole.permissionCodes()), versioned.version(),
                accountId.equals(context.actorAccountId()));
    }

    private static Set<String> permissionSet(
            AuthorizationApplicationService.ReplaceRolePermissionsCommand command) {
        if (command == null || command.permissionCodes() == null) {
            throw new IllegalArgumentException("permissionCodes is required");
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String raw : command.permissionCodes()) {
            if (raw == null || raw.isBlank()) {
                throw new IdentityException(IdentityErrorCode.INVALID_PERMISSION);
            }
            String code = raw.strip();
            if (!codes.add(code)) {
                throw new IllegalArgumentException("permissionCodes must be unique");
            }
        }
        return Set.copyOf(codes);
    }

    private static void validatePermissions(String roleCode, Set<String> replacements) {
        if (!PermissionCatalog.all().containsAll(replacements)) {
            throw new IdentityException(IdentityErrorCode.INVALID_PERMISSION);
        }
        try {
            RolePermissionPolicy.validateRolePermissionSet(roleCode, replacements);
        } catch (IllegalStateException exception) {
            throw new IdentityException(IdentityErrorCode.INVALID_PERMISSION_COMBINATION, exception);
        }
    }

    private String actorDisplayName(IdentityOperationContext context) {
        return accounts.findById(context.actorAccountId())
                .map(EmployeeAccount::displayName)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.AUTHENTICATION_REQUIRED));
    }

    private static String normalizeRoleCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IdentityException(IdentityErrorCode.INVALID_ROLE);
        }
        return raw.strip().toUpperCase(Locale.ROOT);
    }

    private static List<String> sorted(Set<String> codes) {
        return codes.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return values;
    }
}
