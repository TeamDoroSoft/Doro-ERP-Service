package com.dorosoft.erp.identity.domain.role;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 역할 상속 없이 역할의 최종 Permission 집합을 검증한다. */
public final class RolePermissionPolicy {
    private static final Set<String> NON_ADMIN_EXACTLY_FORBIDDEN = Set.of(
            "audit.read",
            "privacy.access-log.read",
            "reservation.policy.manage"
    );

    private static final Map<String, Set<String>> DEPENDENCIES = Map.ofEntries(
            Map.entry("identity.account.create", Set.of("identity.account.read", "identity.role.read")),
            Map.entry("identity.account.status.update", Set.of("identity.account.read")),
            Map.entry("identity.account.unlock", Set.of("identity.account.read")),
            Map.entry("identity.credential.reset", Set.of("identity.account.read")),
            Map.entry("identity.role.assign", Set.of("identity.account.read", "identity.role.read")),
            Map.entry("identity.role.update", Set.of("identity.role.read")),
            Map.entry("table.manage", Set.of("table.read")),
            Map.entry("table.session.manage", Set.of("table.read")),
            Map.entry("table.session.move", Set.of("table.read")),
            Map.entry("table.order.read", Set.of("table.read")),
            Map.entry("order.status.update", Set.of("order.read")),
            Map.entry("order.cancel", Set.of("order.read")),
            Map.entry("order.history.read", Set.of("order.read")),
            Map.entry("payment.manual.complete", Set.of("order.read")),
            Map.entry("payment.manual.correct", Set.of("order.read")),
            Map.entry("inventory.initialize", Set.of("inventory.read")),
            Map.entry("inventory.receive", Set.of("inventory.read")),
            Map.entry("inventory.adjust", Set.of("inventory.read")),
            Map.entry("inventory.safety-stock.manage", Set.of("inventory.read")),
            Map.entry("inventory.history.read", Set.of("inventory.read")),
            Map.entry("inventory.alert.read", Set.of("inventory.read")),
            Map.entry("sales.close", Set.of("sales.closing.read")),
            Map.entry("sales.correct", Set.of("sales.closing.read", "order.read")),
            Map.entry("waiting.call", Set.of("waiting.read")),
            Map.entry("waiting.admit", Set.of("waiting.read")),
            Map.entry("waiting.cancel", Set.of("waiting.read")),
            Map.entry("waiting.no_show", Set.of("waiting.read")),
            Map.entry("waiting.notification.resend", Set.of("waiting.read")),
            Map.entry("reservation.decide", Set.of("reservation.read")),
            Map.entry("reservation.update", Set.of("reservation.read")),
            Map.entry("reservation.cancel", Set.of("reservation.read")),
            Map.entry("reservation.visit.update", Set.of("reservation.read")),
            Map.entry("reservation.no_show", Set.of("reservation.read")),
            Map.entry("reservation.change.decide", Set.of("reservation.read")),
            Map.entry("reservation.policy.manage", Set.of("reservation.policy.read"))
    );

    private RolePermissionPolicy() {
    }

    public static boolean isAssignableToNonAdmin(String permissionCode) {
        Objects.requireNonNull(permissionCode, "permissionCode");
        return PermissionCatalog.contains(permissionCode)
                && !permissionCode.startsWith("identity.")
                && !NON_ADMIN_EXACTLY_FORBIDDEN.contains(permissionCode);
    }

    public static Set<String> requiredPermissions(String permissionCode) {
        return DEPENDENCIES.getOrDefault(permissionCode, Collections.emptySet());
    }

    public static void validateRegistered(Set<String> grantedPermissions) {
        Objects.requireNonNull(grantedPermissions, "grantedPermissions");
        if (!PermissionCatalog.all().containsAll(grantedPermissions)) {
            throw new IllegalStateException("role contains an unregistered permission");
        }
    }

    public static void validateDependencies(String permissionCode, Set<String> grantedPermissions) {
        Objects.requireNonNull(permissionCode, "permissionCode");
        Objects.requireNonNull(grantedPermissions, "grantedPermissions");
        if (!grantedPermissions.containsAll(requiredPermissions(permissionCode))) {
            throw new IllegalStateException("permission dependency is missing");
        }
    }

    public static void validateForNonAdminRole(Set<String> grantedPermissions) {
        validateRegistered(grantedPermissions);
        if (grantedPermissions.stream().anyMatch(permission -> !isAssignableToNonAdmin(permission))) {
            throw new IllegalStateException("non-admin role contains an administrator-only permission");
        }
        validateAllDependencies(grantedPermissions);
    }

    public static void validateForAdminRole(Set<String> grantedPermissions) {
        validateRegistered(grantedPermissions);
        if (!grantedPermissions.equals(PermissionCatalog.all())) {
            throw new IllegalStateException("ADMIN must contain the complete permission catalog");
        }
    }

    public static void validateRolePermissionSet(String roleCode, Set<String> grantedPermissions) {
        RoleCode code = new RoleCode(roleCode);
        if (code.isAdmin()) {
            validateForAdminRole(grantedPermissions);
        } else {
            validateForNonAdminRole(grantedPermissions);
        }
    }

    /** 기존 호출 호환용이며 비관리자 역할 정책을 적용한다. */
    public static void validateRolePermissionSet(Set<String> grantedPermissions) {
        validateForNonAdminRole(grantedPermissions);
    }

    private static void validateAllDependencies(Set<String> grantedPermissions) {
        grantedPermissions.forEach(permission -> validateDependencies(permission, grantedPermissions));
    }
}
