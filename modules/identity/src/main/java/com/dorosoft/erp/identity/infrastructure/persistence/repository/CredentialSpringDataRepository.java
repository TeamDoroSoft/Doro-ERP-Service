package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.CredentialJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialSpringDataRepository extends JpaRepository<CredentialJpaEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from CredentialJpaEntity credential where credential.accountId = :accountId")
    Optional<CredentialJpaEntity> findByAccountIdForUpdate(@Param("accountId") String accountId);
}
