package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyKeyReusedException;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyRequestHash;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 생성(FR-MENU-003). 서버가 마지막 순서를 부여한다.
 * Idempotency-Key 재요청은 같은 내용이면 기존 결과를, 다른 내용이면 거부를 반환한다(생성 API 공통 계약).
 */
@Service
public class CreateCategoryService {

    private final CategoryRepository categoryRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;

    public CreateCategoryService(CategoryRepository categoryRepository, CatalogRevisionRepository catalogRevisionRepository) {
        this.categoryRepository = categoryRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
    }

    @Transactional
    public Category create(String name, String idempotencyKey) {
        String requestHash = IdempotencyRequestHash.of(name);

        if (idempotencyKey != null) {
            Category existing = categoryRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                if (!requestHash.equals(existing.idempotencyRequestHash())) {
                    throw new IdempotencyKeyReusedException(idempotencyKey);
                }
                return existing;
            }
        }

        UUID catalogId =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"))
                        .catalogId();

        int nextDisplayOrder = categoryRepository.findAll().size();
        Category category =
                Category.create(UUID.randomUUID(), catalogId, name, nextDisplayOrder, Instant.now(), idempotencyKey, requestHash);
        return categoryRepository.save(category);
    }
}
