package com.dorosoft.erp.audit.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class Feature17AuditContracts {

    private static final Set<AuditAction> IDENTITY_REQUIRED_ACTIONS = EnumSet.of(
            AuditAction.EMPLOYEE_ACCOUNT_CREATED,
            AuditAction.EMPLOYEE_ACCOUNT_ACTIVATED,
            AuditAction.EMPLOYEE_ACCOUNT_DEACTIVATED,
            AuditAction.ACCOUNT_LOGIN_UNLOCKED,
            AuditAction.EMPLOYEE_PERMISSIONS_CHANGED,
            AuditAction.ROLE_PERMISSIONS_CHANGED
    );

    private Feature17AuditContracts() {
    }

    public static Set<AuditAction> identityActions() {
        return EnumSet.copyOf(IDENTITY_REQUIRED_ACTIONS);
    }

    public static AuditTargetType primaryTargetFor(AuditAction action) {
        return AuditActionSchemaRegistry.primaryTargetFor(action);
    }

    public static boolean isIdentityAction(AuditAction action) {
        return IDENTITY_REQUIRED_ACTIONS.contains(action);
    }

    public static void ensureSupportedAction(AuditAction action, AuditTargetType primaryTargetType) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(primaryTargetType, "primaryTargetType");

        AuditTargetType registeredPrimaryTarget = AuditActionSchemaRegistry.primaryTargetFor(action);
        if (registeredPrimaryTarget != primaryTargetType) {
            throw new IllegalArgumentException("Primary target is invalid for action. action=" + action + ", target=" + primaryTargetType);
        }
    }
}
