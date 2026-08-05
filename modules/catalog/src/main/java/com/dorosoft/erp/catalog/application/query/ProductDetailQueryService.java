package com.dorosoft.erp.catalog.application.query;

import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 Product 단건 조회(GET /catalog/products/{productId}). */
@Service
public class ProductDetailQueryService {

    private final ProductRepository productRepository;

    public ProductDetailQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Product findById(UUID productId) {
        return productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
