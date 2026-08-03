package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditTargetType;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyKeyReusedException;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyRequestHash;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 생성(FR-MENU-003). 서버가 마지막 순서를 부여한다.
 * Idempotency-Key 재요청은 같은 내용이면 기존 결과를, 다른 내용이면 거부를 반환한다(생성 API 공통 계약).
 */
@Service
public class CreateCategoryService {

    private static final String VALUE_SCHEMA_VERSION = "v1";

    private final CategoryRepository categoryRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final AuditWriter auditWriter;

    public CreateCategoryService(
            CategoryRepository categoryRepository, CatalogRevisionRepository catalogRevisionRepository, AuditWriter auditWriter) {
        this.categoryRepository = categoryRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public Category create(String name, String idempotencyKey, AuditContext auditContext) {
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
        Category saved = categoryRepository.save(category);

        auditWriter.record(
                AuditRecordCommand.of(
                        "CATALOG",
                        "CATEGORY_CREATED",
                        UUID.randomUUID().toString(),
                        0,
                        new AuditTarget(AuditTargetType.CATEGORY, saved.categoryId().toString()),
                        "{}",
                        CatalogAuditValues.write(Map.of("name", saved.name(), "version", saved.version())),
                        VALUE_SCHEMA_VERSION),
                auditContext);

        return saved;
    }
}
