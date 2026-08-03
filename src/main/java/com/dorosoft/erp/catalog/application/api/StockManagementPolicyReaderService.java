package com.dorosoft.erp.catalog.application.api;

import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StockManagementPolicyReaderService implements StockManagementPolicyReader {

    private final ProductRepository productRepository;

    StockManagementPolicyReaderService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStockManaged(UUID productId) {
        return productRepository
                .findById(productId)
                .map(Product::stockManaged)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
