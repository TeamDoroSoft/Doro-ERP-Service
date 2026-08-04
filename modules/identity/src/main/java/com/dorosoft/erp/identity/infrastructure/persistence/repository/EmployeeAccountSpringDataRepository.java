package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.EmployeeAccountJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

public interface EmployeeAccountSpringDataRepository
        extends JpaRepository<EmployeeAccountJpaEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from EmployeeAccountJpaEntity account where account.id = :id")
    Optional<EmployeeAccountJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from EmployeeAccountJpaEntity account where account.loginIdNormalized = :loginId")
    Optional<EmployeeAccountJpaEntity> findByLoginIdForUpdate(@Param("loginId") String normalizedLoginId);

    List<EmployeeAccountJpaEntity> findAllByOrderByIdAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmployeeAccountJpaEntity account
            set account.version = account.version + 1
            where account.id = :id and account.version = :expectedVersion
            """)
    int incrementVersion(@Param("id") String id, @Param("expectedVersion") long expectedVersion);
}
