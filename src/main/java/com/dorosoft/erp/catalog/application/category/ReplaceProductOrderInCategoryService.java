package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.ProductOrderRepository;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import com.dorosoft.erp.catalog.domain.category.DisplayOrderValidation;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category 안의 Product 순서 원자적 교체(FR-MENU-008). 판매 비활성 상품도 모두 포함해야 하며
 * 다른 Category 상품·중복·누락은 전체 요청을 거부한다. Product Aggregate 자체는 MENU-04 소유이므로
 * 이 Use Case는 ProductOrderRepository의 좁은 계약만 사용한다.
 */
@Service
public class ReplaceProductOrderInCategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductOrderRepository productOrderRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;

    public ReplaceProductOrderInCategoryService(
            CategoryRepository categoryRepository,
            ProductOrderRepository productOrderRepository,
            CatalogRevisionRepository catalogRevisionRepository) {
        this.categoryRepository = categoryRepository;
        this.productOrderRepository = productOrderRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
    }

    @Transactional
    public CatalogRevision replaceOrder(UUID categoryId, List<UUID> orderedProductIds, long expectedCatalogRevision) {
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw new CategoryNotFoundException(categoryId);
        }

        List<UUID> existingIds = productOrderRepository.findProductIdsByCategory(categoryId);
        DisplayOrderValidation.requireExactIdSet(existingIds, orderedProductIds);

        CatalogRevision current =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"));
        if (current.revision() != expectedCatalogRevision) {
            throw new OptimisticLockingFailureException(
                    "Catalog Revision이 다른 요청에서 변경되었습니다. 요청 revision=" + expectedCatalogRevision + ", 현재 revision="
                            + current.revision());
        }

        productOrderRepository.replaceDisplayOrder(categoryId, orderedProductIds);
        return catalogRevisionRepository.save(current);
    }
}
