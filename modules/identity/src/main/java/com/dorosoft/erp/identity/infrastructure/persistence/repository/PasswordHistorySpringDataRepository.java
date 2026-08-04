package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.PasswordHistoryJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistorySpringDataRepository extends JpaRepository<PasswordHistoryJpaEntity, String> {
    List<PasswordHistoryJpaEntity> findByAccountIdOrderByChangedAtDesc(String accountId);
}
