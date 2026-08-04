package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.EmployeeRoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeRoleSpringDataRepository extends JpaRepository<EmployeeRoleJpaEntity, String> {
    List<EmployeeRoleJpaEntity> findByRoleIdOrderByAccountIdAsc(String roleId);
}
