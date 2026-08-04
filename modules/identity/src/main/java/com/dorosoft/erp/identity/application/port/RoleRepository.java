package com.dorosoft.erp.identity.application.port;

import com.dorosoft.erp.identity.domain.role.Role;
import com.dorosoft.erp.identity.domain.role.RoleAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {
    Optional<Role> findByCode(String roleCode);

    Optional<Role> findByCodeForUpdate(String roleCode);

    List<Role> findActiveRoles();

    List<PermissionDefinition> findPermissions();

    List<UUID> findAccountIdsByRoleId(UUID roleId);

    Role save(Role role);

    Optional<RoleAssignment> findAssignment(UUID accountId);

    void saveAssignment(RoleAssignment assignment);

    record PermissionDefinition(String code, String description) {
        public PermissionDefinition {
            if (code == null || code.isBlank() || description == null || description.isBlank()) {
                throw new IllegalArgumentException("permission definition is incomplete");
            }
        }
    }
}
