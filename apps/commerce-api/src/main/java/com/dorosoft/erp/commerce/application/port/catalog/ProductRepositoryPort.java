package com.dorosoft.erp.commerce.application.port.catalog;

import com.dorosoft.erp.commerce.domain.catalog.Product;
import java.util.Collection;
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

    /** 활성 Category의 활성·판매 가능(비품절) 상품만 표시 순서대로 반환한다 (FR-CATALOG-004). */
    List<Product> findSalesMenuProducts(UUID tenantId);

    /**
     * 주문용 일괄 조회. 요청한 ID 중 이 Tenant가 소유한 상품만 반환한다.
     *
     * <p>판매 가능 여부로 거르지 않는다. 존재하지만 판매 불가인 경우와 아예 없는 경우를
     * Application이 구분해 서로 다른 오류로 응답해야 하기 때문이다.
     */
    List<Product> findAllByTenantAndIds(UUID tenantId, Collection<UUID> productIds);

    boolean existsByTenantAndNameExcludingId(UUID tenantId, String name, UUID excludedProductId);

    boolean existsByTenantAndCategory(UUID tenantId, UUID categoryId);
}
