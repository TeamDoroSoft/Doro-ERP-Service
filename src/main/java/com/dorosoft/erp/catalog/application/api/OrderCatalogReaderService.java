package com.dorosoft.erp.catalog.application.api;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import com.dorosoft.erp.catalog.domain.product.ProductOption;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderCatalogReaderService implements OrderCatalogReader {

    private final ProductRepository productRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;

    OrderCatalogReaderService(ProductRepository productRepository, CatalogRevisionRepository catalogRevisionRepository) {
        this.productRepository = productRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderCatalogSnapshot resolveForOrder(UUID productId, List<UUID> selectedOptionIds) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        List<ProductOption> resolvedOptions = product.resolveOrderableOptions(selectedOptionIds);

        long catalogRevision =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"))
                        .revision();

        List<SelectedOptionSnapshot> selected =
                resolvedOptions.stream()
                        .map(o -> new SelectedOptionSnapshot(o.optionId(), o.name(), o.additionalPrice()))
                        .toList();

        return new OrderCatalogSnapshot(
                product.productId(), product.name(), product.basePrice(), selected, product.stockManaged(), catalogRevision);
    }
}
