package com.dorosoft.erp.catalog.infrastructure.persistence;

import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.domain.category.Category;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** JPA 엔티티와 도메인 Category 사이의 변환을 전담한다. 엔티티는 이 패키지 밖으로 나가지 않는다. */
@Repository
public class JpaCategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;

    public JpaCategoryRepositoryAdapter(CategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Category> findAll() {
        return jpaRepository.findAllByOrderByDisplayOrderAsc().stream().map(JpaCategoryRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<Category> findById(UUID categoryId) {
        return jpaRepository.findById(categoryId).map(JpaCategoryRepositoryAdapter::toDomain);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity = jpaRepository.findById(category.categoryId()).orElse(null);
        if (entity == null) {
            entity = new CategoryEntity(category.categoryId(), category.catalogId(), category.name(), category.displayOrder());
        } else {
            if (entity.getVersion() != category.version()) {
                throw new OptimisticLockingFailureException(
                        "Category가 다른 Transaction에서 변경되었습니다. categoryId="
                                + category.categoryId()
                                + ", 요청 version="
                                + category.version()
                                + ", 현재 version="
                                + entity.getVersion());
            }
            entity.rename(category.name());
        }
        CategoryEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    /** display_order >= 0 CHECK 제약을 지키면서 UNIQUE 충돌도 피하는 임시 오프셋. */
    private static final int REORDER_TEMP_OFFSET = 1_000_000;

    @Override
    public void replaceDisplayOrder(List<UUID> orderedCategoryIds) {
        List<CategoryEntity> entities = jpaRepository.findAllById(orderedCategoryIds);
        Map<UUID, CategoryEntity> byId = entities.stream().collect(Collectors.toMap(CategoryEntity::getCategoryId, e -> e));

        // 1단계: 큰 양수 오프셋으로 임시 이동해 (catalog_id, display_order) UNIQUE 제약 충돌을 피한다.
        // display_order >= 0 CHECK 제약이 있어 음수는 사용할 수 없다.
        for (int i = 0; i < orderedCategoryIds.size(); i++) {
            byId.get(orderedCategoryIds.get(i)).changeDisplayOrder(REORDER_TEMP_OFFSET + i);
        }
        jpaRepository.flush();

        // 2단계: 0부터 최종 값을 부여한다.
        for (int i = 0; i < orderedCategoryIds.size(); i++) {
            byId.get(orderedCategoryIds.get(i)).changeDisplayOrder(i);
        }
        jpaRepository.flush();
    }

    private static Category toDomain(CategoryEntity entity) {
        return new Category(
                entity.getCategoryId(),
                entity.getCatalogId(),
                entity.getName(),
                entity.getDisplayOrder(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
