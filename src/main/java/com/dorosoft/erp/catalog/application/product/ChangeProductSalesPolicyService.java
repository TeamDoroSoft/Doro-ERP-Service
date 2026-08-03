package com.dorosoft.erp.catalog.application.product;

import com.dorosoft.erp.catalog.application.audit.CatalogAuditValues;
import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.application.port.audit.AuditContext;
import com.dorosoft.erp.catalog.application.port.audit.AuditRecordCommand;
import com.dorosoft.erp.catalog.application.port.audit.AuditTarget;
import com.dorosoft.erp.catalog.application.port.audit.AuditTargetType;
import com.dorosoft.erp.catalog.application.port.audit.AuditWriter;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 활성화·재고 관리 대상 여부 변경(FR-MENU-005, 007). 관리자만 호출한다(catalog.manage).
 * initialQuantity를 받는 stockManaged 최초 활성화·재활성화·Inventory Alert 해결은
 * Composition Root의 StockManagementPolicyCoordinator 책임이며, 이 Service는 그 조정
 * 결과로 반영될 Catalog 쪽 값(salesEnabled·stockManaged)만 저장한다. Inventory 모듈이
 * 아직 없어 Coordinator 자체는 이번 카드 범위 밖이다.
 */
@Service
public class ChangeProductSalesPolicyService {

    private static final String VALUE_SCHEMA_VERSION = "v1";

    private final ProductRepository productRepository;
    private final AuditWriter auditWriter;

    public ChangeProductSalesPolicyService(ProductRepository productRepository, AuditWriter auditWriter) {
        this.productRepository = productRepository;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public Product changePolicy(
            UUID productId, boolean salesEnabled, boolean stockManaged, long expectedVersion, AuditContext auditContext) {
        Product current = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (current.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Product가 다른 요청에서 변경되었습니다. productId=" + productId + ", 요청 version=" + expectedVersion
                            + ", 현재 version=" + current.version());
        }
        if (current.salesEnabled() == salesEnabled && current.stockManaged() == stockManaged) {
            return current;
        }
        Product saved = productRepository.save(current.changeSalesPolicy(salesEnabled, stockManaged));

        auditWriter.record(
                new AuditRecordCommand(
                        "CATALOG",
                        "PRODUCT_SALES_POLICY_CHANGED",
                        UUID.randomUUID().toString(),
                        0,
                        new AuditTarget(AuditTargetType.PRODUCT, productId.toString()),
                        null,
                        CatalogAuditValues.write(
                                CatalogAuditValues.map(
                                        "salesEnabled", current.salesEnabled(),
                                        "stockManaged", current.stockManaged(),
                                        "version", current.version())),
                        CatalogAuditValues.write(
                                CatalogAuditValues.map(
                                        "salesEnabled", saved.salesEnabled(),
                                        "stockManaged", saved.stockManaged(),
                                        "version", saved.version())),
                        null,
                        null,
                        VALUE_SCHEMA_VERSION),
                auditContext);

        return saved;
    }
}
