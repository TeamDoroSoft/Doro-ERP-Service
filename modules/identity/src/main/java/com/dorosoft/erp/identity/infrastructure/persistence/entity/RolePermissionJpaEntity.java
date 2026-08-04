package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_permission")
public class RolePermissionJpaEntity {
    @EmbeddedId
    private RolePermissionId id;

    protected RolePermissionJpaEntity() {
    }

    public RolePermissionJpaEntity(String roleId, String permissionId) {
        this.id = new RolePermissionId(roleId, permissionId);
    }
}
