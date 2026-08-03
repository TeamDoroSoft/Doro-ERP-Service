package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.category.DisplayOrderValidation;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Category 전체 순서 원자적 교체(FR-MENU-003). Catalog Revision으로 동시성을 제어한다. */
@Service
public class ReplaceCategoryOrderService {

    private final CategoryRepository categoryRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;

    public ReplaceCategoryOrderService(
            CategoryRepository categoryRepository, CatalogRevisionRepository catalogRevisionRepository) {
        this.categoryRepository = categoryRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
    }

    @Transactional
    public CatalogRevision replaceOrder(List<UUID> orderedCategoryIds, long expectedCatalogRevision) {
        List<UUID> existingIds = categoryRepository.findAll().stream().map(Category::categoryId).toList();
        DisplayOrderValidation.requireExactIdSet(existingIds, orderedCategoryIds);

        CatalogRevision current =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"));
        if (current.revision() != expectedCatalogRevision) {
            throw new OptimisticLockingFailureException(
                    "Catalog Revision이 다른 요청에서 변경되었습니다. 요청 revision=" + expectedCatalogRevision + ", 현재 revision="
                            + current.revision());
        }

        categoryRepository.replaceDisplayOrder(orderedCategoryIds);
        return catalogRevisionRepository.save(current);
    }
}
