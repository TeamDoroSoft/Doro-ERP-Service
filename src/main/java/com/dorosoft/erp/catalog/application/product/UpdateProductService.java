package com.dorosoft.erp.catalog.application.product;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 기본 정보 전체 교체(FR-MENU-002). soldOut과 options는 이 Use Case가 다루지 않는다.
 * 가격·대표 이미지가 실제로 바뀐 경우 PRODUCT_UPDATED 외에 PRODUCT_PRICE_CHANGED·
 * PRODUCT_IMAGE_CHANGED를 같은 operationId·증가하는 eventSequence로 추가 기록한다(5.17).
 */
@Service
public class UpdateProductService {

    private static final String VALUE_SCHEMA_VERSION = "v1";
    private static final String CURRENCY = "KRW";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMediaRepository productMediaRepository;
    private final AuditWriter auditWriter;

    public UpdateProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMediaRepository productMediaRepository,
            AuditWriter auditWriter) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMediaRepository = productMediaRepository;
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
                        : (int) productRepository.countByCategory(command.categoryId());

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
        Product saved = productRepository.save(updated);

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

        if (before.basePrice() != after.basePrice()) {
            auditWriter.record(
                    new AuditRecordCommand(
                            "CATALOG",
                            "PRODUCT_PRICE_CHANGED",
                            operationId,
                            sequence++,
                            target,
                            null,
                            CatalogAuditValues.write(
                                    CatalogAuditValues.map("basePrice", before.basePrice(), "currency", CURRENCY, "version", before.version())),
                            CatalogAuditValues.write(
                                    CatalogAuditValues.map("basePrice", after.basePrice(), "currency", CURRENCY, "version", after.version())),
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
