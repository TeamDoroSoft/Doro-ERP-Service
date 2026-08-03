package com.dorosoft.erp.catalog.application.port;

import com.dorosoft.erp.catalog.domain.category.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    /** displayOrder 오름차순으로 현재 업체의 모든 Category를 반환한다. */
    List<Category> findAll();

    Optional<Category> findById(UUID categoryId);

    /**
     * 새 Category면 삽입하고, 기존 Category면 전달한 version이 저장된 값과 다를 때
     * OptimisticLockingFailureException을 던진다. 이 메서드는 name만 반영하며 displayOrder는 바꾸지 않는다.
     */
    Category save(Category category);

    /** 전체 Category 순서를 0부터 다시 부여한다. 대상 집합 검증은 호출자(Application Service)가 미리 마친다. */
    void replaceDisplayOrder(List<UUID> orderedCategoryIds);

    /** 이전에 사용된 Idempotency-Key라면 그 결과 Category를 반환한다(생성 API 공통 계약). */
    Optional<Category> findByIdempotencyKey(String idempotencyKey);
}
