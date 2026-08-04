package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditRelatedTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditRelationType;
import com.dorosoft.erp.catalog.application.port.audit.AuditTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditTargetType;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.category.DisplayOrderValidation;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Category 전체 순서 원자적 교체(FR-MENU-003). Catalog Revision으로 동시성을 제어한다. */
@Service
public class ReplaceCategoryOrderService {

    private static final String VALUE_SCHEMA_VERSION = "v1";

    private final CategoryRepository categoryRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final AuditWriter auditWriter;

    public ReplaceCategoryOrderService(
            CategoryRepository categoryRepository, CatalogRevisionRepository catalogRevisionRepository, AuditWriter auditWriter) {
        this.categoryRepository = categoryRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public CatalogRevision replaceOrder(List<UUID> orderedCategoryIds, long expectedCatalogRevision, AuditContext auditContext) {
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
        CatalogRevision advanced = catalogRevisionRepository.save(current);

        List<AuditRelatedTarget> relatedCategories =
                orderedCategoryIds.stream()
                        .skip(1)
                        .map(id -> new AuditRelatedTarget(AuditRelationType.CATEGORY, AuditTargetType.CATEGORY, id.toString()))
                        .collect(Collectors.toList());
        auditWriter.record(
                new AuditRecordCommand(
                        "CATALOG",
                        "CATEGORY_ORDER_CHANGED",
                        UUID.randomUUID().toString(),
                        0,
                        new AuditTarget(AuditTargetType.CATEGORY, orderedCategoryIds.get(0).toString()),
                        relatedCategories,
                        CatalogAuditValues.write(Map.of("categoryIds", existingIds, "catalogRevision", current.revision())),
                        CatalogAuditValues.write(
                                Map.of("categoryIds", orderedCategoryIds, "catalogRevision", advanced.revision())),
                        null,
                        null,
                        VALUE_SCHEMA_VERSION),
                auditContext);

        return advanced;
    }
}
