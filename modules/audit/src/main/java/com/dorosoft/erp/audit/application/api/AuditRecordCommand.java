package com.dorosoft.erp.audit.application.api;

import com.dorosoft.erp.audit.domain.AuditAction;
import com.dorosoft.erp.audit.domain.AuditDomain;
import com.dorosoft.erp.audit.domain.AuditPrimaryTarget;
import com.dorosoft.erp.audit.domain.AuditRelatedTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditRecordCommand(
        AuditDomain domain,
        AuditAction action,
        UUID operationId,
        int eventSequence,
        AuditPrimaryTarget primaryTarget,
        List<AuditRelatedTarget> relatedTargets,
        Map<String, Object> beforeValue,
        Map<String, Object> afterValue,
        String reasonCode,
        String reason,
        int valueSchemaVersion
) {

    public AuditRecordCommand {
        relatedTargets = relatedTargets == null ? Collections.emptyList() : List.copyOf(relatedTargets);
        beforeValue = immutableMapAllowingNullValues(beforeValue);
        afterValue = immutableMapAllowingNullValues(afterValue);
    }

    private static Map<String, Object> immutableMapAllowingNullValues(Map<String, Object> value) {
        return value == null || value.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    public static AuditRecordCommandBuilder builder() {
        return new AuditRecordCommandBuilder();
    }

    public static AuditRecordCommand employeeAccountCreated(
            UUID operationId,
            UUID accountId,
            UUID roleId,
            Map<String, Object> afterValue
    ) {
        return identity(
                AuditAction.EMPLOYEE_ACCOUNT_CREATED,
                operationId,
                new AuditPrimaryTarget(com.dorosoft.erp.audit.domain.AuditTargetType.ACCOUNT, accountId),
                List.of(new AuditRelatedTarget(
                        com.dorosoft.erp.audit.domain.AuditRelationType.SUBJECT_ROLE,
                        com.dorosoft.erp.audit.domain.AuditTargetType.ROLE,
                        roleId)),
                Map.of(),
                afterValue,
                null
        );
    }

    public static AuditRecordCommand employeeAccountActivated(
            UUID operationId,
            UUID accountId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue
    ) {
        return identityAccountState(
                AuditAction.EMPLOYEE_ACCOUNT_ACTIVATED,
                operationId,
                accountId,
                beforeValue,
                afterValue,
                null
        );
    }

    public static AuditRecordCommand employeeAccountDeactivated(
            UUID operationId,
            UUID accountId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue
    ) {
        return identityAccountState(
                AuditAction.EMPLOYEE_ACCOUNT_DEACTIVATED,
                operationId,
                accountId,
                beforeValue,
                afterValue,
                null
        );
    }

    public static AuditRecordCommand accountLoginUnlocked(
            UUID operationId,
            UUID accountId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue,
            String reason
    ) {
        return identityAccountState(
                AuditAction.ACCOUNT_LOGIN_UNLOCKED,
                operationId,
                accountId,
                beforeValue,
                afterValue,
                reason
        );
    }

    public static AuditRecordCommand rolePermissionsChanged(
            UUID operationId,
            UUID roleId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue
    ) {
        return identity(
                AuditAction.ROLE_PERMISSIONS_CHANGED,
                operationId,
                new AuditPrimaryTarget(com.dorosoft.erp.audit.domain.AuditTargetType.ROLE, roleId),
                List.of(),
                beforeValue,
                afterValue,
                null
        );
    }

    public static AuditRecordCommand employeePermissionsChanged(
            UUID operationId,
            UUID accountId,
            UUID beforeRoleId,
            UUID afterRoleId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue
    ) {
        List<AuditRelatedTarget> roles = Objects.equals(beforeRoleId, afterRoleId)
                ? List.of(subjectRole(beforeRoleId))
                : List.of(subjectRole(beforeRoleId), subjectRole(afterRoleId));
        return identity(
                AuditAction.EMPLOYEE_PERMISSIONS_CHANGED,
                operationId,
                new AuditPrimaryTarget(com.dorosoft.erp.audit.domain.AuditTargetType.ACCOUNT, accountId),
                roles,
                beforeValue,
                afterValue,
                null
        );
    }

    private static AuditRecordCommand identityAccountState(
            AuditAction action,
            UUID operationId,
            UUID accountId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue,
            String reason
    ) {
        return identity(
                action,
                operationId,
                new AuditPrimaryTarget(com.dorosoft.erp.audit.domain.AuditTargetType.ACCOUNT, accountId),
                List.of(),
                beforeValue,
                afterValue,
                reason
        );
    }

    private static AuditRecordCommand identity(
            AuditAction action,
            UUID operationId,
            AuditPrimaryTarget primaryTarget,
            List<AuditRelatedTarget> relatedTargets,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue,
            String reason
    ) {
        return new AuditRecordCommand(
                AuditDomain.IDENTITY,
                action,
                operationId,
                0,
                primaryTarget,
                relatedTargets,
                beforeValue,
                afterValue,
                null,
                reason,
                1
        );
    }

    private static AuditRelatedTarget subjectRole(UUID roleId) {
        return new AuditRelatedTarget(
                com.dorosoft.erp.audit.domain.AuditRelationType.SUBJECT_ROLE,
                com.dorosoft.erp.audit.domain.AuditTargetType.ROLE,
                roleId
        );
    }

    public static final class AuditRecordCommandBuilder {
        private AuditDomain domain;
        private AuditAction action;
        private UUID operationId;
        private int eventSequence;
        private AuditPrimaryTarget primaryTarget;
        private java.util.List<AuditRelatedTarget> relatedTargets = Collections.emptyList();
        private Map<String, Object> beforeValue = Collections.emptyMap();
        private Map<String, Object> afterValue = Collections.emptyMap();
        private String reasonCode;
        private String reason;
        private int valueSchemaVersion = 1;

        private AuditRecordCommandBuilder() {
        }

        public AuditRecordCommandBuilder domain(AuditDomain domain) {
            this.domain = domain;
            return this;
        }

        public AuditRecordCommandBuilder action(AuditAction action) {
            this.action = action;
            return this;
        }

        public AuditRecordCommandBuilder operationId(UUID operationId) {
            this.operationId = operationId;
            return this;
        }

        public AuditRecordCommandBuilder eventSequence(int eventSequence) {
            this.eventSequence = eventSequence;
            return this;
        }

        public AuditRecordCommandBuilder primaryTarget(AuditPrimaryTarget primaryTarget) {
            this.primaryTarget = primaryTarget;
            return this;
        }

        public AuditRecordCommandBuilder relatedTargets(java.util.List<AuditRelatedTarget> relatedTargets) {
            this.relatedTargets = relatedTargets;
            return this;
        }

        public AuditRecordCommandBuilder beforeValue(Map<String, Object> beforeValue) {
            this.beforeValue = beforeValue;
            return this;
        }

        public AuditRecordCommandBuilder afterValue(Map<String, Object> afterValue) {
            this.afterValue = afterValue;
            return this;
        }

        public AuditRecordCommandBuilder reasonCode(String reasonCode) {
            this.reasonCode = reasonCode;
            return this;
        }

        public AuditRecordCommandBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public AuditRecordCommandBuilder valueSchemaVersion(int valueSchemaVersion) {
            this.valueSchemaVersion = valueSchemaVersion;
            return this;
        }

        public AuditRecordCommand build() {
            return new AuditRecordCommand(
                    domain,
                    action,
                    operationId,
                    eventSequence,
                    primaryTarget,
                    relatedTargets,
                    beforeValue,
                    afterValue,
                    reasonCode,
                    reason,
                    valueSchemaVersion
            );
        }
    }
}
