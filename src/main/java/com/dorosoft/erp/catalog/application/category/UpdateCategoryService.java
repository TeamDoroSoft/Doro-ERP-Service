package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditTargetType;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리명 변경(FR-MENU-003). expectedVersion은 클라이언트의 If-Match 값에 대응한다. */
@Service
public class UpdateCategoryService {

    private static final String VALUE_SCHEMA_VERSION = "v1";

    private final CategoryRepository categoryRepository;
    private final AuditWriter auditWriter;

    public UpdateCategoryService(CategoryRepository categoryRepository, AuditWriter auditWriter) {
        this.categoryRepository = categoryRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public Category rename(UUID categoryId, String newName, long expectedVersion, AuditContext auditContext) {
        Category current = categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (current.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Category가 다른 요청에서 변경되었습니다. categoryId=" + categoryId + ", 요청 version=" + expectedVersion
                            + ", 현재 version=" + current.version());
        }
        Category updated = categoryRepository.save(current.rename(newName));

        auditWriter.record(
                AuditRecordCommand.of(
                        "CATALOG",
                        "CATEGORY_UPDATED",
                        UUID.randomUUID().toString(),
                        0,
                        new AuditTarget(AuditTargetType.CATEGORY, categoryId.toString()),
                        CatalogAuditValues.write(Map.of("name", current.name(), "version", current.version())),
                        CatalogAuditValues.write(Map.of("name", updated.name(), "version", updated.version())),
                        VALUE_SCHEMA_VERSION),
                auditContext);

        return updated;
    }
}
