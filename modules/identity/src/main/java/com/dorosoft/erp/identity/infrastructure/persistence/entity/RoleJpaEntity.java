package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "role")
public class RoleJpaEntity {
    @Id
    @Column(name = "role_id", nullable = false, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RoleJpaEntity() {
    }

    public static RoleJpaEntity of(UUID id, String code, String name, boolean active, long version) {
        RoleJpaEntity entity = new RoleJpaEntity();
        entity.id = id.toString();
        entity.code = code;
        entity.name = name;
        entity.active = active;
        entity.version = version;
        return entity;
    }

    public UUID roleId() { return UUID.fromString(id); }
    public String code() { return code; }
    public String name() { return name; }
    public boolean active() { return active; }
    public long version() { return version; }
}
