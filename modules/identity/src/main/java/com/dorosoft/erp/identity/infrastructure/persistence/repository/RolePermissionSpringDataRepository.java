package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.RolePermissionId;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.RolePermissionJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionSpringDataRepository
        extends JpaRepository<RolePermissionJpaEntity, RolePermissionId> {
    @Query(value = """
            SELECT permission.code
            FROM role_permission mapping
            JOIN permission permission ON permission.permission_id = mapping.permission_id
            WHERE mapping.role_id = :roleId
            ORDER BY permission.code
            """, nativeQuery = true)
    List<String> findPermissionCodesByRoleId(@Param("roleId") String roleId);

    @Modifying
    @Query(value = "DELETE FROM role_permission WHERE role_id = :roleId", nativeQuery = true)
    void deleteByRoleId(@Param("roleId") String roleId);
}
