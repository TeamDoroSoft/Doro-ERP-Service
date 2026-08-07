package com.dorosoft.erp.commerce.application.port.catalog;

import com.dorosoft.erp.commerce.domain.catalog.MenuCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Category 저장소 계약. 모든 조회·변경은 Tenant Scope를 Query 조건으로 포함한다.
 */
public interface MenuCategoryRepositoryPort {

    MenuCategory insert(MenuCategory category);

    /**
     * {@code id + tenantId + version} 조건부 Update.
     *
     * @return 반영된 Row 수. 0이면 Tenant 불일치·미존재 또는 version 충돌이다.
     */
    int updateWithVersion(MenuCategory category, long expectedVersion);

    Optional<MenuCategory> findByTenantAndId(UUID tenantId, UUID categoryId);

    List<MenuCategory> findAllByTenant(UUID tenantId);

    List<MenuCategory> findActiveByTenant(UUID tenantId);

    boolean existsByTenantAndNameExcludingId(UUID tenantId, String name, UUID excludedCategoryId);
}
