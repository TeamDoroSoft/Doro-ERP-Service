package com.dorosoft.erp.commerce.infrastructure.persistence.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuCategoryJpaRepository extends JpaRepository<MenuCategoryEntity, UUID> {

    Optional<MenuCategoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<MenuCategoryEntity> findByTenantIdOrderByDisplayOrderAscIdAsc(UUID tenantId);

    List<MenuCategoryEntity> findByTenantIdAndStatusOrderByDisplayOrderAscIdAsc(UUID tenantId, String status);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);

    /**
     * version 조건부 Update. 조건에 맞는 Row가 없으면 0을 반환해 Lost Update를 만들지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MenuCategoryEntity c
               set c.name = :name,
                   c.displayOrder = :displayOrder,
                   c.status = :status,
                   c.version = c.version + 1,
                   c.updatedAt = :updatedAt
             where c.id = :id
               and c.tenantId = :tenantId
               and c.version = :expectedVersion
            """)
    int updateWithVersion(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            @Param("displayOrder") int displayOrder,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") Instant updatedAt);
}
