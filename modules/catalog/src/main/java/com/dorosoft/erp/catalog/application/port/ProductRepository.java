package com.dorosoft.erp.catalog.application.port;

import com.dorosoft.erp.catalog.domain.product.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Optional<Product> findById(UUID productId);

    /** 새 Product면 삽입하고, 기존 Product면 전달한 version이 저장된 값과 다를 때 OptimisticLockingFailureException을 던진다. */
    Product save(Product product);

    /** 해당 Category에 속한 Product 수(다음 표시 순서 계산용). */
    long countByCategory(UUID categoryId);

    /** 이전에 사용된 Idempotency-Key라면 그 결과 Product를 반환한다(FR-MENU-001 생성 멱등성). */
    Optional<Product> findByIdempotencyKey(String idempotencyKey);

    /** 상태·활성 여부와 무관하게 해당 Category의 모든 Product를 display_order 오름차순으로 반환한다. */
    List<Product> findByCategory(UUID categoryId);

    /** 상태·활성 여부와 무관하게 현재 업체의 모든 Product를 생성 순서(안정적 정렬)로 반환한다. */
    List<Product> findAll();
}
