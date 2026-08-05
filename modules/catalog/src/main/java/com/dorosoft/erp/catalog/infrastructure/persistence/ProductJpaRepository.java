package com.dorosoft.erp.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    @Query("SELECT COALESCE(MAX(e.displayOrder), -1) FROM ProductEntity e WHERE e.categoryId = :categoryId")
    int findMaxDisplayOrder(@Param("categoryId") UUID categoryId);

    /**
     * options는 지연 로딩 컬렉션이라, 이 조회를 감싸는 Transaction이 없는 호출부(CreateProductService의
     * Idempotency-Key 사전 확인·재조회처럼 REQUIRES_NEW 격리를 위해 의도적으로 Service 전체를
     * Transactional로 감싸지 않은 경우)에서도 LazyInitializationException 없이 매핑할 수 있도록
     * 즉시 로딩한다.
     */
    @EntityGraph(attributePaths = "options")
    Optional<ProductEntity> findByIdempotencyKey(String idempotencyKey);

    List<ProductEntity> findByCategoryIdOrderByDisplayOrderAsc(UUID categoryId);

    List<ProductEntity> findAllByOrderByCreatedAtAscProductIdAsc();
}
