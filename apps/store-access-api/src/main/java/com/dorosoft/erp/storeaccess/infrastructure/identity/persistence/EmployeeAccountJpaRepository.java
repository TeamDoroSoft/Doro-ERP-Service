package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmployeeAccountJpaRepository extends JpaRepository<EmployeeAccountEntity, UUID> {

    Optional<EmployeeAccountEntity> findByTenantIdAndLoginId(UUID tenantId, String loginId);
}
