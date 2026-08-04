package com.dorosoft.erp.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    long countByCategoryId(UUID categoryId);

    Optional<ProductEntity> findByIdempotencyKey(String idempotencyKey);

    List<ProductEntity> findByCategoryIdOrderByDisplayOrderAsc(UUID categoryId);

    List<ProductEntity> findAllByOrderByCreatedAtAscProductIdAsc();
}
