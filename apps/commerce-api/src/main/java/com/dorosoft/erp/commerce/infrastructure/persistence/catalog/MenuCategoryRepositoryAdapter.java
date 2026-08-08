package com.dorosoft.erp.commerce.infrastructure.persistence.catalog;

import com.dorosoft.erp.commerce.application.port.catalog.MenuCategoryRepositoryPort;
import com.dorosoft.erp.commerce.domain.catalog.CatalogStatus;
import com.dorosoft.erp.commerce.domain.catalog.MenuCategory;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class MenuCategoryRepositoryAdapter implements MenuCategoryRepositoryPort {

    private final MenuCategoryJpaRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;

    MenuCategoryRepositoryAdapter(
            MenuCategoryJpaRepository repository, EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public MenuCategory insert(MenuCategory category) {
        Instant now = clock.instant();
        MenuCategoryEntity entity = new MenuCategoryEntity(
                category.id(),
                category.tenantId(),
                category.name(),
                category.displayOrder(),
                category.status().name(),
                0L,
                now,
                now);
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public int updateWithVersion(MenuCategory category, long expectedVersion) {
        return repository.updateWithVersion(
                category.id(),
                category.tenantId(),
                category.name(),
                category.displayOrder(),
                category.status().name(),
                expectedVersion,
                clock.instant());
    }

    @Override
    public Optional<MenuCategory> findByTenantAndId(UUID tenantId, UUID categoryId) {
        if (tenantId == null || categoryId == null) {
            return Optional.empty();
        }
        return repository.findByIdAndTenantId(categoryId, tenantId).map(MenuCategoryRepositoryAdapter::toDomain);
    }

    @Override
    public List<MenuCategory> findAllByTenant(UUID tenantId) {
        return repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId).stream()
                .map(MenuCategoryRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<MenuCategory> findActiveByTenant(UUID tenantId) {
        return repository
                .findByTenantIdAndStatusOrderByDisplayOrderAscIdAsc(tenantId, CatalogStatus.ACTIVE.name())
                .stream()
                .map(MenuCategoryRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<MenuCategory> findAllByTenantAndIds(UUID tenantId, Collection<UUID> categoryIds) {
        if (tenantId == null || categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return repository.findByTenantIdAndIdIn(tenantId, categoryIds).stream()
                .map(MenuCategoryRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean existsByTenantAndNameExcludingId(UUID tenantId, String name, UUID excludedCategoryId) {
        if (excludedCategoryId == null) {
            return repository.existsByTenantIdAndName(tenantId, name);
        }
        return repository.existsByTenantIdAndNameAndIdNot(tenantId, name, excludedCategoryId);
    }

    private static MenuCategory toDomain(MenuCategoryEntity entity) {
        return MenuCategory.restore(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getDisplayOrder(),
                CatalogStatus.valueOf(entity.getStatus()),
                entity.getVersion());
    }
}
