package com.dorosoft.erp.identity.infrastructure.persistence;

import com.dorosoft.erp.identity.application.port.RoleRepository;
import com.dorosoft.erp.identity.domain.role.Role;
import com.dorosoft.erp.identity.domain.role.RoleAssignment;
import com.dorosoft.erp.identity.domain.role.RoleCode;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.EmployeeRoleJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.RoleJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.RolePermissionJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.EmployeeRoleSpringDataRepository;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.PermissionSpringDataRepository;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.RolePermissionSpringDataRepository;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.RoleSpringDataRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaRoleRepositoryAdapter implements RoleRepository {
    private final RoleSpringDataRepository roleRepository;
    private final PermissionSpringDataRepository permissionRepository;
    private final RolePermissionSpringDataRepository rolePermissionRepository;
    private final EmployeeRoleSpringDataRepository employeeRoleRepository;

    public JpaRoleRepositoryAdapter(
            RoleSpringDataRepository roleRepository,
            PermissionSpringDataRepository permissionRepository,
            RolePermissionSpringDataRepository rolePermissionRepository,
            EmployeeRoleSpringDataRepository employeeRoleRepository
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.employeeRoleRepository = employeeRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByCode(String roleCode) {
        return roleRepository.findByCode(roleCode).map(this::toDomain);
    }

    @Override
    public Optional<Role> findByCodeForUpdate(String roleCode) {
        return roleRepository.findByCodeForUpdate(roleCode).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> findActiveRoles() {
        return roleRepository.findByActiveTrueOrderByCodeAsc().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionDefinition> findPermissions() {
        return permissionRepository.findAllByOrderByCodeAsc().stream()
                .map(permission -> new PermissionDefinition(permission.code(), permission.description()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findAccountIdsByRoleId(UUID roleId) {
        return employeeRoleRepository.findByRoleIdOrderByAccountIdAsc(roleId.toString()).stream()
                .map(entity -> UUID.fromString(entity.accountId()))
                .toList();
    }

    @Override
    public Role save(Role role) {
        RoleJpaEntity saved = roleRepository.saveAndFlush(RoleJpaEntity.of(
                role.roleId(), role.code().value(), role.name(), role.active(), role.version()
        ));
        var permissions = permissionRepository.findByCodeIn(role.permissionCodes());
        if (permissions.size() != role.permissionCodes().size()) {
            throw new IllegalStateException("cannot persist an unregistered permission");
        }
        rolePermissionRepository.deleteByRoleId(saved.roleId().toString());
        rolePermissionRepository.saveAll(permissions.stream()
                .map(permission -> new RolePermissionJpaEntity(
                        saved.roleId().toString(), permission.id()
                ))
                .toList());
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoleAssignment> findAssignment(UUID accountId) {
        return employeeRoleRepository.findById(accountId.toString()).map(entity -> {
            RoleJpaEntity role = roleRepository.findById(entity.roleId())
                    .orElseThrow(() -> new IllegalStateException("assigned role is missing"));
            return new RoleAssignment(
                    accountId, role.roleId(), new RoleCode(role.code()),
                    entity.assignedBy() == null ? null : UUID.fromString(entity.assignedBy()),
                    entity.assignmentSource(), entity.assignedAt()
            );
        });
    }

    @Override
    public void saveAssignment(RoleAssignment assignment) {
        employeeRoleRepository.saveAndFlush(EmployeeRoleJpaEntity.of(
                assignment.employeeAccountId().toString(),
                assignment.roleId().toString(),
                assignment.assignedBy() == null ? null : assignment.assignedBy().toString(),
                assignment.assignmentSource(),
                assignment.assignedAt()
        ));
    }

    private Role toDomain(RoleJpaEntity entity) {
        Set<String> permissions = Set.copyOf(
                rolePermissionRepository.findPermissionCodesByRoleId(entity.roleId().toString())
        );
        return new Role(
                entity.roleId(), new RoleCode(entity.code()), entity.name(),
                entity.active(), entity.version(), permissions
        );
    }
}
