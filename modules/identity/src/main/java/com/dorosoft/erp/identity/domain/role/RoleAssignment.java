package com.dorosoft.erp.identity.domain.role;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 계정당 하나만 존재하는 역할 할당. */
public record RoleAssignment(
        UUID employeeAccountId,
        UUID roleId,
        RoleCode roleCode,
        UUID assignedBy,
        AssignmentSource assignmentSource,
        Instant assignedAt
) {
    public RoleAssignment {
        Objects.requireNonNull(employeeAccountId, "employeeAccountId");
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(roleCode, "roleCode");
        Objects.requireNonNull(assignmentSource, "assignmentSource");
        Objects.requireNonNull(assignedAt, "assignedAt");
        if (assignmentSource == AssignmentSource.BOOTSTRAP) {
            if (assignedBy != null || !roleCode.isAdmin()) {
                throw new IllegalArgumentException("BOOTSTRAP assignment must be an unassigned ADMIN role");
            }
        } else if (assignedBy == null) {
            throw new IllegalArgumentException("ADMIN assignment requires assignedBy");
        }
    }

    public RoleAssignment replaceRole(UUID nextRoleId, RoleCode nextRoleCode, UUID actorId, Instant now) {
        Objects.requireNonNull(nextRoleCode, "nextRoleCode");
        if (assignmentSource == AssignmentSource.BOOTSTRAP) {
            if (!nextRoleCode.isAdmin()) {
                throw new IllegalStateException("representative ADMIN role is immutable");
            }
            return this;
        }
        return new RoleAssignment(employeeAccountId, nextRoleId, nextRoleCode, actorId, AssignmentSource.ADMIN, now);
    }
}
