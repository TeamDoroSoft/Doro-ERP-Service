package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
import com.dorosoft.erp.catalog.application.concurrency.DisplayOrderConflictSupport;
import com.dorosoft.erp.catalog.application.concurrency.IdempotentWinnerLookup;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 카테고리 생성(FR-MENU-003). 서버가 마지막 순서를 부여한다.
 * Idempotency-Key 재요청은 같은 내용이면 기존 결과를, 다른 내용이면 거부를 반환한다(생성 API 공통 계약).
 *
 * <p>동시 생성 방어: 실제 삽입 시도는 별도 Transaction(REQUIRES_NEW)에서 실행한다. nextDisplayOrder는 이
 * Transaction들을 시작하기 전에 딱 한 번만 계산해 재시도 사이에 절대 바뀌지 않는다 — 그래야 "같은 Catalog
 * 안에서 동시 요청 중 하나만 성공"이 재시도 여부와 무관하게 유지된다. 같은 Catalog 안에서 동시에 마지막
 * 순서를 다투면 실제 MySQL/InnoDB 동작은 두 갈래로 나타난다 — (1) uk_category_display_order UNIQUE
 * 위반({@link DataIntegrityViolationException}) — 실제 데이터 충돌이 확정된 것이므로 재시도하지 않고
 * OptimisticLockingFailureException으로 번역해 그대로 전파한다(서버가 재시도하지 않는다). (2) 표시 순서와
 * 무관한 공유 catalog_revision 행 경합 등으로 InnoDB가 순환 대기를 감지해 Transaction을 강제 종료하는 경우
 * ({@link CannotAcquireLockException}, "Deadlock found when trying to get lock") — 이는 실제 데이터
 * 충돌이 아니라 일시적인 잠금 스케줄링 문제이므로(서로 다른 Catalog 사이에서도 발생할 수 있다), 같은
 * nextDisplayOrder로 짧게 재시도한다(MySQL 공식 권고사항이며, "서버가 실패한 요청을 자동 재시도하지 않는다"는
 * 정책은 확정된 충돌에 대한 것으로 이 일시적 재시도와는 무관하다). 두 경우 모두 최종적으로 실패하면 먼저 같은
 * Idempotency-Key로 재조회해 동시에 들어온 동일 요청이면 그 결과로 수렴시키고(승자의 Commit이 아직 반영되지
 * 않았을 수 있어 짧게 재시도하며 기다린다), 그렇지 않으면 위와 같이 처리한다. uk_category_idempotency_key
 * UNIQUE 위반은 재조회 결과가 다른 내용이면 IdempotencyKeyReusedException으로 수렴시킨다 — 실패한 삽입
 * 시도 자체는 격리된 Transaction 안에서만 Rollback되므로 이 재조회는 오염되지 않은 새 Transaction에서
 * 실행된다.
 */
@Service
public class CreateCategoryService {

    private static final String VALUE_SCHEMA_VERSION = "v1";
    private static final int MAX_DEADLOCK_RETRY_ATTEMPTS = 3;

    private final CategoryRepository categoryRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final AuditWriter auditWriter;
    private final TransactionTemplate requiresNewTransaction;

    public CreateCategoryService(
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

    public Category create(String name, String idempotencyKey, AuditContext auditContext) {
        String requestHash = IdempotencyRequestHash.of(name);

        Category existing = findExisting(idempotencyKey, requestHash);
        if (existing != null) {
            return existing;
        }

        UUID catalogId =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"))
                        .catalogId();
        int nextDisplayOrder = categoryRepository.nextDisplayOrder(catalogId);

        for (int attempt = 1; ; attempt++) {
            try {
                return requiresNewTransaction.execute(
                        status ->
                                createAndRecord(
                                        name, idempotencyKey, requestHash, catalogId, nextDisplayOrder, auditContext));
            } catch (CannotAcquireLockException ex) {
                if (attempt >= MAX_DEADLOCK_RETRY_ATTEMPTS) {
                    Category winner = reconcileIdempotentWinner(idempotencyKey, requestHash);
                    if (winner != null) {
                        return winner;
                    }
                    throw new OptimisticLockingFailureException(
                            "반복된 Deadlock으로 Category 생성에 실패했습니다. name=" + name, ex);
                }
            } catch (DataIntegrityViolationException ex) {
                Category winner = reconcileIdempotentWinner(idempotencyKey, requestHash);
                if (winner != null) {
                    return winner;
                }
                if (DisplayOrderConflictSupport.matchesConstraint(ex, "uk_category_display_order")) {
                    throw new OptimisticLockingFailureException(
                            "Category 표시 순서가 동시 생성·유입 요청과 충돌했습니다. name=" + name, ex);
                }
                throw ex;
            }
        }
    }

    /** 이전에 사용된 Idempotency-Key라면 같은 내용은 기존 결과를, 다른 내용은 거부를 반환한다. */
    private Category findExisting(String idempotencyKey, String requestHash) {
        if (idempotencyKey == null) {
            return null;
        }
        Category existing = categoryRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!requestHash.equals(existing.idempotencyRequestHash())) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        return existing;
    }

    /**
     * uk_category_idempotency_key 위반으로 삽입이 실패했을 가능성을 확인한다. Idempotency-Key가 없는
     * 요청이거나, 재조회해도 대상이 없다면(=다른 원인의 무결성 위반) null을 반환해 원래 예외를 그대로
     * 전파하게 한다.
     */
    private Category reconcileIdempotentWinner(String idempotencyKey, String requestHash) {
        if (idempotencyKey == null) {
            return null;
        }
        Category winner =
                IdempotentWinnerLookup.await(() -> categoryRepository.findByIdempotencyKey(idempotencyKey).orElse(null));
        if (winner == null) {
            return null;
        }
        if (!requestHash.equals(winner.idempotencyRequestHash())) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        return winner;
    }

    private Category createAndRecord(
            String name,
            String idempotencyKey,
            String requestHash,
            UUID catalogId,
            int nextDisplayOrder,
            AuditContext auditContext) {
        Category category =
                Category.create(UUID.randomUUID(), catalogId, name, nextDisplayOrder, Instant.now(), idempotencyKey, requestHash);
        Category saved = categoryRepository.save(category);
        catalogRevisionRepository.advance();

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
