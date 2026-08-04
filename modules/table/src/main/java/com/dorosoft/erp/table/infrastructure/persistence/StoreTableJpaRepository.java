package com.dorosoft.erp.table.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreTableJpaRepository extends JpaRepository<StoreTableEntity, UUID> {

    Optional<StoreTableEntity> findByNormalizedNumber(String normalizedNumber);

    List<StoreTableEntity> findAllByOrderByNormalizedNumberAsc();
}
