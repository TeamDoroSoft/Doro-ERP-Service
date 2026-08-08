package com.dorosoft.erp.commerce.infrastructure.persistence.catalog;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ProductEntity> findByTenantIdOrderByDisplayOrderAscIdAsc(UUID tenantId);

    boolean existsByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    /**
     * POS·Kiosk 공통 판매 메뉴 조회 (FR-CATALOG-004).
     *
     * <p>활성 Category에 속한 활성 상품 중 품절이 아닌 것만 반환한다. 비활성·품절 Row는
     * 삭제하지 않고 조회에서만 제외한다. 채널별 분기가 없으므로 POS와 Kiosk가 같은 결과를 받는다.
     */
    @Query("""
            select p from ProductEntity p, MenuCategoryEntity c
             where p.tenantId = :tenantId
               and c.tenantId = :tenantId
               and p.categoryId = c.id
               and p.status = 'ACTIVE'
               and c.status = 'ACTIVE'
               and p.soldOut = false
             order by c.displayOrder asc, c.id asc, p.displayOrder asc, p.id asc
            """)
    List<ProductEntity> findSalesMenuProducts(@Param("tenantId") UUID tenantId);

    /** 주문용 조회. 존재 여부와 Tenant 소유만 확인하고 판매 가능 여부는 Domain이 판단한다. */
    List<ProductEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> productIds);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ProductEntity p
               set p.categoryId = :categoryId,
                   p.name = :name,
                   p.description = :description,
                   p.price = :price,
                   p.status = :status,
                   p.displayOrder = :displayOrder,
                   p.version = p.version + 1,
                   p.updatedAt = :updatedAt
             where p.id = :id
               and p.tenantId = :tenantId
               and p.version = :expectedVersion
            """)
    int updateWithVersion(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("categoryId") UUID categoryId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("price") long price,
            @Param("status") String status,
            @Param("displayOrder") int displayOrder,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") Instant updatedAt);

    /**
     * 품절 상태만 바꾸는 조건부 Update.
     *
     * <p>같은 상품을 동시에 변경해도 version이 일치하는 요청 하나만 성공하고
     * 나머지는 0 Row를 받아 Conflict로 처리된다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ProductEntity p
               set p.soldOut = :soldOut,
                   p.version = p.version + 1,
                   p.updatedAt = :updatedAt
             where p.id = :id
               and p.tenantId = :tenantId
               and p.version = :expectedVersion
            """)
    int updateSoldOutWithVersion(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("soldOut") boolean soldOut,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") Instant updatedAt);
}
