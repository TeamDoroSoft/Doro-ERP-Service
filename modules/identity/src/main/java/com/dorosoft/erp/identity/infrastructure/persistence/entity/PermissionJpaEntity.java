package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permission")
public class PermissionJpaEntity {
    @Id
    @Column(name = "permission_id", nullable = false, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "code", nullable = false, length = 120, unique = true)
    private String code;
    @Column(name = "description", nullable = false, length = 255)
    private String description;

    protected PermissionJpaEntity() {
    }

    public String id() { return id; }
    public String code() { return code; }
    public String description() { return description; }
}
