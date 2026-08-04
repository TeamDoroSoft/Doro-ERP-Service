package com.dorosoft.erp.identity.domain.role;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 역할 상속 없이 최종 Permission 집합을 소유하는 역할. */
public record Role(
        UUID roleId,
        RoleCode code,
        String name,
        boolean active,
        long version,
        Set<String> permissionCodes
) {
    public Role {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(permissionCodes, "permissionCodes");
        if (name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("role name must contain 1 to 100 characters");
        }
        if (version < 0) {
            throw new IllegalArgumentException("role version must be greater than or equal to 0");
        }
        permissionCodes = Set.copyOf(permissionCodes);
        RolePermissionPolicy.validateRolePermissionSet(code.value(), permissionCodes);
    }

    public Role replacePermissions(Set<String> replacement) {
        return new Role(roleId, code, name, active, version, replacement);
    }
}
