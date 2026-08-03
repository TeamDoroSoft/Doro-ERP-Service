package com.dorosoft.erp.catalog.application.product;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditTargetType;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import com.dorosoft.erp.catalog.domain.product.ProductOption;
import com.dorosoft.erp.catalog.domain.product.ProductOptionRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 옵션 전체 교체(FR-MENU-004). Product Aggregate를 통해서만 옵션을 바꾸며 Product version을 증가시킨다. */
@Service
public class ReplaceProductOptionsService {

    private static final String VALUE_SCHEMA_VERSION = "v1";

    private final ProductRepository productRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final AuditWriter auditWriter;

    public ReplaceProductOptionsService(
            ProductRepository productRepository, CatalogRevisionRepository catalogRevisionRepository, AuditWriter auditWriter) {
        this.productRepository = productRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public Product replaceOptions(
            UUID productId, List<ProductOptionRequest> options, long expectedVersion, AuditContext auditContext) {
        Product current = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (current.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Product가 다른 요청에서 변경되었습니다. productId=" + productId + ", 요청 version=" + expectedVersion
                            + ", 현재 version=" + current.version());
        }
        Product saved = productRepository.save(current.replaceOptions(options));
        catalogRevisionRepository.advance();

        auditWriter.record(
                new AuditRecordCommand(
                        "CATALOG",
                        "PRODUCT_OPTIONS_CHANGED",
                        UUID.randomUUID().toString(),
                        0,
                        new AuditTarget(AuditTargetType.PRODUCT, productId.toString()),
                        null,
                        CatalogAuditValues.write(
                                CatalogAuditValues.map("options", toAuditOptions(current.options()), "version", current.version())),
                        CatalogAuditValues.write(
                                CatalogAuditValues.map("options", toAuditOptions(saved.options()), "version", saved.version())),
                        null,
                        null,
                        VALUE_SCHEMA_VERSION),
                auditContext);

        return saved;
    }

    private static List<Map<String, Object>> toAuditOptions(List<ProductOption> options) {
        return options.stream()
                .map(
                        option ->
                                CatalogAuditValues.map(
                                        "optionId", option.optionId(),
                                        "name", option.name(),
                                        "additionalPrice", option.additionalPrice(),
                                        "enabled", option.enabled(),
                                        "displayOrder", option.displayOrder()))
                .toList();
    }
}
