package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmployeeSecurityHistoryJpaRepository
        extends JpaRepository<EmployeeSecurityHistoryEntity, UUID>,
        JpaSpecificationExecutor<EmployeeSecurityHistoryEntity> {

    @Modifying
    @Query(
            value = "DELETE FROM employee_security_history WHERE id IN "
                    + "(SELECT id FROM employee_security_history WHERE expires_at <= :now "
                    + "ORDER BY expires_at LIMIT :batchSize)",
            nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
