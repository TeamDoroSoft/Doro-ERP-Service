package com.dorosoft.erp.catalog.application.product;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
import com.dorosoft.erp.catalog.application.concurrency.DisplayOrderConflictSupport;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditTargetType;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import com.dorosoft.erp.catalog.domain.media.MediaNotFoundException;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import com.dorosoft.erp.catalog.domain.product.MediaNotReadyException;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 기본 정보 전체 교체(FR-MENU-002). soldOut과 options는 이 Use Case가 다루지 않는다.
 * 가격·대표 이미지가 실제로 바뀐 경우 PRODUCT_UPDATED 외에 PRODUCT_PRICE_CHANGED·
 * PRODUCT_IMAGE_CHANGED를 같은 operationId·증가하는 eventSequence로 추가 기록한다(5.17).
 *
 * <p>다른 Product가 이동해 들어오며 대상 Category의 마지막 순서를 다투면 uk_product_display_order UNIQUE
 * 위반({@link DataIntegrityViolationException}) 또는 InnoDB 순환 대기로 인한 강제 종료
 * ({@link CannotAcquireLockException})가 나타날 수 있다. 둘 다 OptimisticLockingFailureException으로
 * 번역해 그대로 전파한다(동시 생성·유입 충돌, 서버가 재시도하지 않는다).
 */
@Service
public class UpdateProductService {

    private static final String VALUE_SCHEMA_VERSION = "v1";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMediaRepository productMediaRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final AuditWriter auditWriter;

    public UpdateProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMediaRepository productMediaRepository,
            CatalogRevisionRepository catalogRevisionRepository,
            AuditWriter auditWriter) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMediaRepository = productMediaRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public Product replaceBasicInfo(
            UUID productId, ReplaceProductBasicInfoCommand command, long expectedVersion, AuditContext auditContext) {
        Product current = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (current.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Product가 다른 요청에서 변경되었습니다. productId=" + productId + ", 요청 version=" + expectedVersion
                            + ", 현재 version=" + current.version());
        }

        if (categoryRepository.findById(command.categoryId()).isEmpty()) {
            throw new CategoryNotFoundException(command.categoryId());
        }
        validateMediaReady(command.mediaId());

        int displayOrder =
                current.categoryId().equals(command.categoryId())
                        ? current.displayOrder()
                        : productRepository.nextDisplayOrder(command.categoryId());

        Product updated =
                current.replaceBasicInfo(
                        command.categoryId(),
                        command.mediaId(),
                        command.name(),
                        command.description(),
                        command.basePrice(),
                        command.imageAltText(),
                        command.salesEnabled(),
                        command.stockManaged(),
                        displayOrder);
        Product saved;
        try {
            saved = productRepository.save(updated);
            catalogRevisionRepository.advance();
        } catch (DataIntegrityViolationException | CannotAcquireLockException ex) {
            if (ex instanceof CannotAcquireLockException
                    || DisplayOrderConflictSupport.matchesConstraint(
                            (DataIntegrityViolationException) ex, "uk_product_display_order")) {
                throw new OptimisticLockingFailureException(
                        "Product 표시 순서가 동시 생성·유입 요청과 충돌했습니다. categoryId=" + command.categoryId(), ex);
            }
            throw ex;
        }

        recordAudit(current, saved, auditContext);

        return saved;
    }

    private void recordAudit(Product before, Product after, AuditContext auditContext) {
        String operationId = UUID.randomUUID().toString();
        AuditTarget target = new AuditTarget(AuditTargetType.PRODUCT, after.productId().toString());
        int sequence = 0;

        auditWriter.record(
                new AuditRecordCommand(
                        "CATALOG",
                        "PRODUCT_UPDATED",
                        operationId,
                        sequence++,
                        target,
                        null,
                        CatalogAuditValues.write(
                                CatalogAuditValues.map(
                                        "name", before.name(),
                                        "description", before.description(),
                                        "categoryId", before.categoryId(),
                                        "version", before.version())),
                        CatalogAuditValues.write(
                                CatalogAuditValues.map(
                                        "name", after.name(),
                                        "description", after.description(),
                                        "categoryId", after.categoryId(),
                                        "version", after.version())),
                        null,
                        null,
                        VALUE_SCHEMA_VERSION),
                auditContext);

        if (!before.basePrice().equals(after.basePrice())) {
            auditWriter.record(
                    new AuditRecordCommand(
                            "CATALOG",
                            "PRODUCT_PRICE_CHANGED",
                            operationId,
                            sequence++,
                            target,
                            null,
                            CatalogAuditValues.write(
                                    CatalogAuditValues.map(
                                            "basePrice", before.basePrice().amount(),
                                            "currency", before.basePrice().currency(),
                                            "version", before.version())),
                            CatalogAuditValues.write(
                                    CatalogAuditValues.map(
                                            "basePrice", after.basePrice().amount(),
                                            "currency", after.basePrice().currency(),
                                            "version", after.version())),
                            null,
                            null,
                            VALUE_SCHEMA_VERSION),
                    auditContext);
        }

        if (!Objects.equals(before.mediaId(), after.mediaId()) || !Objects.equals(before.imageAltText(), after.imageAltText())) {
            auditWriter.record(
                    new AuditRecordCommand(
                            "CATALOG",
                            "PRODUCT_IMAGE_CHANGED",
                            operationId,
                            sequence++,
                            target,
                            null,
                            CatalogAuditValues.write(
                                    CatalogAuditValues.map(
                                            "mediaId", before.mediaId(), "imageAltText", before.imageAltText(), "version", before.version())),
                            CatalogAuditValues.write(
                                    CatalogAuditValues.map(
                                            "mediaId", after.mediaId(), "imageAltText", after.imageAltText(), "version", after.version())),
                            null,
                            null,
                            VALUE_SCHEMA_VERSION),
                    auditContext);
        }
    }

    private void validateMediaReady(UUID mediaId) {
        if (mediaId == null) {
            return;
        }
        ProductMedia media = productMediaRepository.findById(mediaId).orElseThrow(() -> new MediaNotFoundException(mediaId));
        if (!media.isReady()) {
            throw new MediaNotReadyException(mediaId);
        }
    }
}
