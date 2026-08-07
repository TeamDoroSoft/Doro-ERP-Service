package com.dorosoft.erp.commerce.support;

import com.dorosoft.erp.commerce.application.port.catalog.MenuCategoryRepositoryPort;
import com.dorosoft.erp.commerce.application.port.catalog.ProductRepositoryPort;
import com.dorosoft.erp.commerce.domain.catalog.CatalogStatus;
import com.dorosoft.erp.commerce.domain.catalog.MenuCategory;
import com.dorosoft.erp.commerce.domain.catalog.Product;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Use Case 규칙 검증용 In-memory 저장소.
 *
 * <p>Database Constraint와 실제 Lock 동작은 Testcontainers 통합 Test가 담당한다.
 */
public final class InMemoryCatalogRepositories {

    private final Map<UUID, MenuCategory> categories = new LinkedHashMap<>();
    private final Map<UUID, Product> products = new LinkedHashMap<>();

    public MenuCategoryRepositoryPort categoryRepository() {
        return new MenuCategoryRepositoryPort() {

            @Override
            public MenuCategory insert(MenuCategory category) {
                categories.put(category.id(), category);
                return category;
            }

            @Override
            public int updateWithVersion(MenuCategory category, long expectedVersion) {
                MenuCategory stored = categories.get(category.id());
                if (stored == null
                        || !stored.belongsTo(category.tenantId())
                        || stored.version() != expectedVersion) {
                    return 0;
                }
                categories.put(
                        category.id(),
                        MenuCategory.restore(
                                category.id(),
                                category.tenantId(),
                                category.name(),
                                category.displayOrder(),
                                category.status(),
                                expectedVersion + 1));
                return 1;
            }

            @Override
            public Optional<MenuCategory> findByTenantAndId(UUID tenantId, UUID categoryId) {
                return Optional.ofNullable(categories.get(categoryId))
                        .filter(category -> category.belongsTo(tenantId));
            }

            @Override
            public List<MenuCategory> findAllByTenant(UUID tenantId) {
                return categories.values().stream()
                        .filter(category -> category.belongsTo(tenantId))
                        .sorted(Comparator.comparingInt(MenuCategory::displayOrder))
                        .toList();
            }

            @Override
            public List<MenuCategory> findActiveByTenant(UUID tenantId) {
                return findAllByTenant(tenantId).stream().filter(MenuCategory::isActive).toList();
            }

            @Override
            public List<MenuCategory> findAllByTenantAndIds(UUID tenantId, Collection<UUID> categoryIds) {
                if (categoryIds == null || categoryIds.isEmpty()) {
                    return List.of();
                }
                return categories.values().stream()
                        .filter(category -> category.belongsTo(tenantId))
                        .filter(category -> categoryIds.contains(category.id()))
                        .toList();
            }

            @Override
            public boolean existsByTenantAndNameExcludingId(UUID tenantId, String name, UUID excludedCategoryId) {
                return categories.values().stream()
                        .filter(category -> category.belongsTo(tenantId))
                        .filter(category -> !category.id().equals(excludedCategoryId))
                        .anyMatch(category -> sameName(category.name(), name));
            }
        };
    }

    public ProductRepositoryPort productRepository() {
        return new ProductRepositoryPort() {

            @Override
            public Product insert(Product product) {
                products.put(product.id(), product);
                return product;
            }

            @Override
            public int updateWithVersion(Product product, long expectedVersion) {
                Product stored = products.get(product.id());
                if (stored == null || !stored.belongsTo(product.tenantId()) || stored.version() != expectedVersion) {
                    return 0;
                }
                products.put(product.id(), restore(product, stored.soldOut(), expectedVersion + 1));
                return 1;
            }

            @Override
            public int updateSoldOutWithVersion(
                    UUID tenantId, UUID productId, boolean soldOut, long expectedVersion) {
                Product stored = products.get(productId);
                if (stored == null || !stored.belongsTo(tenantId) || stored.version() != expectedVersion) {
                    return 0;
                }
                products.put(productId, restore(stored, soldOut, expectedVersion + 1));
                return 1;
            }

            @Override
            public Optional<Product> findByTenantAndId(UUID tenantId, UUID productId) {
                return Optional.ofNullable(products.get(productId)).filter(product -> product.belongsTo(tenantId));
            }

            @Override
            public List<Product> findAllByTenant(UUID tenantId) {
                return products.values().stream()
                        .filter(product -> product.belongsTo(tenantId))
                        .sorted(Comparator.comparingInt(Product::displayOrder))
                        .toList();
            }

            @Override
            public List<Product> findAllByTenantAndIds(UUID tenantId, Collection<UUID> productIds) {
                if (productIds == null || productIds.isEmpty()) {
                    return List.of();
                }
                return products.values().stream()
                        .filter(product -> product.belongsTo(tenantId))
                        .filter(product -> productIds.contains(product.id()))
                        .toList();
            }

            /** 실제 JPQL과 같이 비활성 Category·비활성 상품·품절 상품을 모두 제외한다. */
            @Override
            public List<Product> findSalesMenuProducts(UUID tenantId) {
                return findAllByTenant(tenantId).stream()
                        .filter(product -> !product.soldOut())
                        .filter(product -> product.isSellableUnder(
                                categories.values().stream()
                                        .filter(category -> category.id().equals(product.categoryId()))
                                        .filter(category -> category.belongsTo(tenantId))
                                        .findFirst()
                                        .orElse(null)))
                        .toList();
            }

            @Override
            public boolean existsByTenantAndNameExcludingId(UUID tenantId, String name, UUID excludedProductId) {
                return products.values().stream()
                        .filter(product -> product.belongsTo(tenantId))
                        .filter(product -> !product.id().equals(excludedProductId))
                        .anyMatch(product -> sameName(product.name(), name));
            }

            @Override
            public boolean existsByTenantAndCategory(UUID tenantId, UUID categoryId) {
                return products.values().stream()
                        .filter(product -> product.belongsTo(tenantId))
                        .anyMatch(product -> product.categoryId().equals(categoryId));
            }
        };
    }

    public MenuCategory seedCategory(UUID tenantId, String name, int displayOrder, CatalogStatus status) {
        MenuCategory category = MenuCategory.restore(UUID.randomUUID(), tenantId, name, displayOrder, status, 0L);
        categories.put(category.id(), category);
        return category;
    }

    public Product seedProduct(
            UUID tenantId, UUID categoryId, String name, long price, CatalogStatus status, boolean soldOut) {
        Product product = Product.restore(
                UUID.randomUUID(), tenantId, categoryId, name, null, price, soldOut, status, 0, 0L);
        products.put(product.id(), product);
        return product;
    }

    /** 같은 Tenant에 같은 이름의 Category가 있으면 재사용하고 없으면 새로 만든다. */
    public MenuCategory findOrSeedCategory(UUID tenantId, String name) {
        return categories.values().stream()
                .filter(category -> category.belongsTo(tenantId))
                .filter(category -> category.name().equals(name))
                .findFirst()
                .orElseGet(() -> seedCategory(tenantId, name, 0, CatalogStatus.ACTIVE));
    }

    public MenuCategory category(UUID categoryId) {
        return categories.get(categoryId);
    }

    public Product product(UUID productId) {
        return products.get(productId);
    }

    private static Product restore(Product source, boolean soldOut, long version) {
        return Product.restore(
                source.id(),
                source.tenantId(),
                source.categoryId(),
                source.name(),
                source.description(),
                source.price(),
                soldOut,
                source.status(),
                source.displayOrder(),
                version);
    }

    /** Database Unique Index와 같은 정확 일치 규칙을 사용한다. */
    private static boolean sameName(String stored, String candidate) {
        return candidate != null && stored.equals(candidate.trim());
    }
}
