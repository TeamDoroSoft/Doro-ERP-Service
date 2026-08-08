package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmployeeAccountJpaRepository extends JpaRepository<EmployeeAccountEntity, UUID> {

    Optional<EmployeeAccountEntity> findByTenantIdAndLoginId(UUID tenantId, String loginId);

    List<EmployeeAccountEntity> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmployeeAccountEntity e "
            + "WHERE e.tenantId = :tenantId "
            + "AND e.role = com.dorosoft.erp.storeaccess.domain.identity.Role.OWNER "
            + "AND e.status = com.dorosoft.erp.storeaccess.domain.identity.EmployeeStatus.ACTIVE "
            + "ORDER BY e.id")
    List<EmployeeAccountEntity> findActiveOwnersForUpdate(@Param("tenantId") UUID tenantId);
}
