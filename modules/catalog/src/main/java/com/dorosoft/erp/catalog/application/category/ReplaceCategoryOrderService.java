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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Category 전체 순서 원자적 교체(FR-MENU-003). Catalog Revision으로 동시성을 제어한다.
 *
 * <p>동시 정렬 방어: 서로 다른 순서로 여러 Category 행을 갱신하는 두 요청이 겹치면 실제 데이터 충돌이
 * 아니라도 InnoDB가 순환 대기를 감지해 Transaction을 강제 종료할 수 있다({@link CannotAcquireLockException},
 * "Deadlock found when trying to get lock"). 이는 {@link CreateCategoryService}가 생성 경합에서 이미
 * 쓰는 것과 같은 일시적 잠금 스케줄링 문제이므로 같은 요청으로 짧게 재시도한다. 재시도해도 실패하면
 * OptimisticLockingFailureException으로 번역해 호출자가 최신 Revision으로 다시 시도하게 한다.
 */
@Service
public class ReplaceCategoryOrderService {

    private static final String VALUE_SCHEMA_VERSION = "v1";
    private static final int MAX_DEADLOCK_RETRY_ATTEMPTS = 3;

    private final CategoryRepository categoryRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final AuditWriter auditWriter;
    private final TransactionTemplate requiresNewTransaction;

    public ReplaceCategoryOrderService(
            CategoryRepository categoryRepository,
            CatalogRevisionRepository catalogRevisionRepository,
            AuditWriter auditWriter,
            PlatformTransactionManager transactionManager) {
        this.categoryRepository = categoryRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
        this.auditWriter = auditWriter;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CatalogRevision replaceOrder(List<UUID> orderedCategoryIds, long expectedCatalogRevision, AuditContext auditContext) {
        for (int attempt = 1; ; attempt++) {
            try {
                return requiresNewTransaction.execute(
                        status -> replaceOrderAndRecord(orderedCategoryIds, expectedCatalogRevision, auditContext));
            } catch (CannotAcquireLockException ex) {
                if (attempt >= MAX_DEADLOCK_RETRY_ATTEMPTS) {
                    throw new OptimisticLockingFailureException(
                            "반복된 Deadlock으로 Category 순서 변경에 실패했습니다.", ex);
                }
            }
        }
    }

    private CatalogRevision replaceOrderAndRecord(
            List<UUID> orderedCategoryIds, long expectedCatalogRevision, AuditContext auditContext) {
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
