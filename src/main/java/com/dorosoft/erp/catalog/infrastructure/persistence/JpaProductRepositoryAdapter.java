package com.dorosoft.erp.catalog.infrastructure.persistence;

import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** JPA 엔티티와 도메인 Product Aggregate 사이의 변환을 전담한다. 엔티티는 이 패키지 밖으로 나가지 않는다. */
@Repository
public class JpaProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    public JpaProductRepositoryAdapter(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Product> findById(UUID productId) {
        return jpaRepository.findById(productId).map(JpaProductRepositoryAdapter::toDomain);
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = jpaRepository.findById(product.productId()).orElse(null);
        if (entity == null) {
            entity =
                    new ProductEntity(
                            product.productId(),
                            product.catalogId(),
                            product.categoryId(),
                            product.mediaId(),
                            product.name(),
                            product.description(),
                            product.basePrice(),
                            product.imageAltText(),
                            product.salesEnabled(),
                            product.soldOut(),
                            product.stockManaged(),
                            product.displayOrder(),
                            product.idempotencyKey(),
                            product.idempotencyRequestHash());
            entity.replaceOptions(toEntities(product.options()));
        } else {
            if (entity.getVersion() != product.version()) {
                throw new OptimisticLockingFailureException(
                        "Product가 다른 Transaction에서 변경되었습니다. productId="
                                + product.productId()
                                + ", 요청 version="
                                + product.version()
                                + ", 현재 version="
                                + entity.getVersion());
            }
            entity.applyBasicInfo(
                    product.categoryId(),
                    product.mediaId(),
                    product.name(),
                    product.description(),
                    product.basePrice(),
                    product.imageAltText(),
                    product.salesEnabled(),
                    product.stockManaged(),
                    product.displayOrder());
            entity.replaceOptions(toEntities(product.options()));
            // OneToMany 자식 컬렉션만 바뀐 저장도 Product version을 올리도록 명시적으로 Dirty 처리한다.
            entity.touch();
        }
        ProductEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public long countByCategory(UUID categoryId) {
        return jpaRepository.countByCategoryId(categoryId);
    }

    @Override
    public Optional<Product> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(JpaProductRepositoryAdapter::toDomain);
    }

    private static List<ProductOptionEntity> toEntities(List<ProductOption> options) {
        return options.stream()
                .map(o -> new ProductOptionEntity(o.optionId(), o.name(), o.additionalPrice(), o.enabled(), o.displayOrder()))
                .toList();
    }

    private static Product toDomain(ProductEntity entity) {
        List<ProductOption> options =
                entity.getOptions().stream()
                        .map(
                                o ->
                                        new ProductOption(
                                                o.getProductOptionId(), o.getName(), o.getAdditionalPrice(), o.isEnabled(), o.getDisplayOrder()))
                        .sorted(java.util.Comparator.comparingInt(ProductOption::displayOrder))
                        .toList();
        return new Product(
                entity.getProductId(),
                entity.getCatalogId(),
                entity.getCategoryId(),
                entity.getMediaId(),
                entity.getName(),
                entity.getDescription(),
                entity.getBasePrice(),
                entity.getImageAltText(),
                entity.isSalesEnabled(),
                entity.isSoldOut(),
                entity.isStockManaged(),
                entity.getDisplayOrder(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                options,
                entity.getIdempotencyKey(),
                entity.getIdempotencyRequestHash());
    }
}
