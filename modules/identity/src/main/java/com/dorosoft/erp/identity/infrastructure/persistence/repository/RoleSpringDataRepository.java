package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.RoleJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleSpringDataRepository extends JpaRepository<RoleJpaEntity, String> {
    Optional<RoleJpaEntity> findByCode(String code);

    List<RoleJpaEntity> findByActiveTrueOrderByCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from RoleJpaEntity role where role.code = :code")
    Optional<RoleJpaEntity> findByCodeForUpdate(@Param("code") String code);
}
