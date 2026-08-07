package com.dorosoft.erp.commerce.infrastructure.persistence.catalog;

import com.dorosoft.erp.commerce.application.port.catalog.ProductRepositoryPort;
import com.dorosoft.erp.commerce.domain.catalog.CatalogStatus;
import com.dorosoft.erp.commerce.domain.catalog.Product;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class ProductRepositoryAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;

    ProductRepositoryAdapter(ProductJpaRepository repository, EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public Product insert(Product product) {
        Instant now = clock.instant();
        ProductEntity entity = new ProductEntity(
                product.id(),
                product.tenantId(),
                product.categoryId(),
                product.name(),
                product.description(),
                product.price(),
                product.soldOut(),
                product.status().name(),
                product.displayOrder(),
                0L,
                now,
                now);
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public int updateWithVersion(Product product, long expectedVersion) {
        return repository.updateWithVersion(
                product.id(),
                product.tenantId(),
                product.categoryId(),
                product.name(),
                product.description(),
                product.price(),
                product.status().name(),
                product.displayOrder(),
                expectedVersion,
                clock.instant());
    }

    @Override
    public int updateSoldOutWithVersion(UUID tenantId, UUID productId, boolean soldOut, long expectedVersion) {
        return repository.updateSoldOutWithVersion(productId, tenantId, soldOut, expectedVersion, clock.instant());
    }

    @Override
    public Optional<Product> findByTenantAndId(UUID tenantId, UUID productId) {
        if (tenantId == null || productId == null) {
            return Optional.empty();
        }
        return repository.findByIdAndTenantId(productId, tenantId).map(ProductRepositoryAdapter::toDomain);
    }

    @Override
    public List<Product> findAllByTenant(UUID tenantId) {
        return repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId).stream()
                .map(ProductRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Product> findAllByTenantAndIds(UUID tenantId, Collection<UUID> productIds) {
        if (tenantId == null || productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return repository.findByTenantIdAndIdIn(tenantId, productIds).stream()
                .map(ProductRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Product> findSalesMenuProducts(UUID tenantId) {
        return repository.findSalesMenuProducts(tenantId).stream()
                .map(ProductRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean existsByTenantAndNameExcludingId(UUID tenantId, String name, UUID excludedProductId) {
        if (excludedProductId == null) {
            return repository.existsByTenantIdAndName(tenantId, name);
        }
        return repository.existsByTenantIdAndNameAndIdNot(tenantId, name, excludedProductId);
    }

    @Override
    public boolean existsByTenantAndCategory(UUID tenantId, UUID categoryId) {
        return repository.existsByTenantIdAndCategoryId(tenantId, categoryId);
    }

    private static Product toDomain(ProductEntity entity) {
        return Product.restore(
                entity.getId(),
                entity.getTenantId(),
                entity.getCategoryId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.isSoldOut(),
                CatalogStatus.valueOf(entity.getStatus()),
                entity.getDisplayOrder(),
                entity.getVersion());
    }
}
