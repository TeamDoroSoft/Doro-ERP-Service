package com.dorosoft.erp.catalog.application.product;

import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import com.dorosoft.erp.catalog.domain.product.ProductOptionRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 옵션 전체 교체(FR-MENU-004). Product Aggregate를 통해서만 옵션을 바꾸며 Product version을 증가시킨다. */
@Service
public class ReplaceProductOptionsService {

    private final ProductRepository productRepository;

    public ReplaceProductOptionsService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product replaceOptions(UUID productId, List<ProductOptionRequest> options, long expectedVersion) {
        Product current = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (current.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Product가 다른 요청에서 변경되었습니다. productId=" + productId + ", 요청 version=" + expectedVersion
                            + ", 현재 version=" + current.version());
        }
        return productRepository.save(current.replaceOptions(options));
    }
}
