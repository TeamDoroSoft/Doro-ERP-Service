package com.dorosoft.erp.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findAllByOrderByDisplayOrderAsc();

    Optional<CategoryEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(MAX(e.displayOrder), -1) FROM CategoryEntity e WHERE e.catalogId = :catalogId")
    int findMaxDisplayOrder(@Param("catalogId") UUID catalogId);
}
