package com.dorosoft.erp.catalog.application.product;

import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.product.ProductNotFoundException;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수동 품절 설정·해제(FR-MENU-006). 관리자 또는 허용된 직원이 호출한다(catalog.soldout.update).
 * 같은 값의 재요청은 성공하며 version을 불필요하게 증가시키지 않는다(API 명세).
 */
@Service
public class ChangeSoldOutService {

    private final ProductRepository productRepository;

    public ChangeSoldOutService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product changeSoldOut(UUID productId, boolean soldOut, long expectedVersion) {
        Product current = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        if (current.soldOut() == soldOut) {
            return current;
        }
        if (current.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Product가 다른 요청에서 변경되었습니다. productId=" + productId + ", 요청 version=" + expectedVersion
                            + ", 현재 version=" + current.version());
        }
        return productRepository.save(current.changeSoldOut(soldOut));
    }
}
