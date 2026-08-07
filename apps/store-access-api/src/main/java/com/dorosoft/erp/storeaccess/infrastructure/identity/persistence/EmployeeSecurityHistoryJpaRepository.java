package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmployeeSecurityHistoryJpaRepository extends JpaRepository<EmployeeSecurityHistoryEntity, UUID> {
}
