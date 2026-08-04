package com.dorosoft.erp.identity.infrastructure.persistence.repository;

import com.dorosoft.erp.identity.infrastructure.persistence.entity.PermissionJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionSpringDataRepository extends JpaRepository<PermissionJpaEntity, String> {
    List<PermissionJpaEntity> findByCodeIn(Collection<String> codes);

    List<PermissionJpaEntity> findAllByOrderByCodeAsc();
}
