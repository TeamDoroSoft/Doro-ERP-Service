package com.dorosoft.erp.commerce.application.port.catalog;

import com.dorosoft.erp.commerce.domain.catalog.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 상품 저장소 계약. 모든 조회·변경은 Tenant Scope를 Query 조건으로 포함한다.
 */
public interface ProductRepositoryPort {

    Product insert(Product product);

    /**
     * {@code id + tenantId + version} 조건부 Update.
     *
     * @return 반영된 Row 수. 0이면 Tenant 불일치·미존재 또는 version 충돌이다.
     */
    int updateWithVersion(Product product, long expectedVersion);

    /**
     * 품절 상태만 바꾸는 조건부 Update. 다른 필드를 덮어쓰지 않아 Lost Update를 만들지 않는다.
     *
     * @return 반영된 Row 수. 0이면 Tenant 불일치·미존재 또는 version 충돌이다.
     */
    int updateSoldOutWithVersion(UUID tenantId, UUID productId, boolean soldOut, long expectedVersion);

    Optional<Product> findByTenantAndId(UUID tenantId, UUID productId);

    List<Product> findAllByTenant(UUID tenantId);

    /** 활성 Category에 속한 활성 상품만 표시 순서대로 반환한다. */
    List<Product> findSalesMenuProducts(UUID tenantId);

    boolean existsByTenantAndNameExcludingId(UUID tenantId, String name, UUID excludedProductId);

    boolean existsByTenantAndCategory(UUID tenantId, UUID categoryId);
}
