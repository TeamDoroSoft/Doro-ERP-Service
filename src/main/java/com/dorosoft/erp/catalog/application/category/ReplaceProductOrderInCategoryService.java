package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.ProductOrderRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditRelatedTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditRelationType;
import com.dorosoft.erp.catalog.application.port.audit.AuditTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditTargetType;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import com.dorosoft.erp.catalog.domain.category.DisplayOrderValidation;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final String VALUE_SCHEMA_VERSION = "v1";

    private final CategoryRepository categoryRepository;
    private final ProductOrderRepository productOrderRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final AuditWriter auditWriter;

    public ReplaceProductOrderInCategoryService(
            CategoryRepository categoryRepository,
            ProductOrderRepository productOrderRepository,
            CatalogRevisionRepository catalogRevisionRepository,
            AuditWriter auditWriter) {
        this.categoryRepository = categoryRepository;
        this.productOrderRepository = productOrderRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public CatalogRevision replaceOrder(
            UUID categoryId, List<UUID> orderedProductIds, long expectedCatalogRevision, AuditContext auditContext) {
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
        CatalogRevision advanced = catalogRevisionRepository.save(current);

        if (!orderedProductIds.isEmpty()) {
            List<AuditRelatedTarget> related = new ArrayList<>();
            related.add(new AuditRelatedTarget(AuditRelationType.CATEGORY, AuditTargetType.CATEGORY, categoryId.toString()));
            orderedProductIds.stream()
                    .skip(1)
                    .forEach(id -> related.add(new AuditRelatedTarget(AuditRelationType.PRODUCT, AuditTargetType.PRODUCT, id.toString())));

            auditWriter.record(
                    new AuditRecordCommand(
                            "CATALOG",
                            "PRODUCT_ORDER_CHANGED",
                            UUID.randomUUID().toString(),
                            0,
                            new AuditTarget(AuditTargetType.PRODUCT, orderedProductIds.get(0).toString()),
                            related,
                            CatalogAuditValues.write(
                                    Map.of("categoryId", categoryId, "productIds", existingIds, "catalogRevision", current.revision())),
                            CatalogAuditValues.write(
                                    Map.of(
                                            "categoryId", categoryId, "productIds", orderedProductIds, "catalogRevision", advanced.revision())),
                            null,
                            null,
                            VALUE_SCHEMA_VERSION),
                    auditContext);
        }

        return advanced;
    }
}
