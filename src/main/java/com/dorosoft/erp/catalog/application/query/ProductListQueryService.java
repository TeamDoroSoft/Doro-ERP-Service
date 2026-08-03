package com.dorosoft.erp.catalog.application.query;

import com.dorosoft.erp.catalog.application.port.ProductRepository;
import com.dorosoft.erp.catalog.domain.product.Product;
import com.dorosoft.erp.catalog.domain.query.InvalidCursorException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 Product 목록 조회(GET /catalog/products). categoryId·salesEnabled·soldOut으로
 * 걸러낸 뒤 cursor 기반으로 한 Page씩 반환한다. 활성 여부와 무관하게 모든 상태를 대상으로 한다.
 */
@Service
public class ProductListQueryService {

    private final ProductRepository productRepository;

    public ProductListQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ProductListPage list(UUID categoryId, Boolean salesEnabled, Boolean soldOut, String cursor, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 1 이상이어야 한다");
        }

        List<Product> candidates = categoryId != null ? productRepository.findByCategory(categoryId) : productRepository.findAll();
        List<Product> filtered =
                candidates.stream()
                        .filter(p -> salesEnabled == null || p.salesEnabled() == salesEnabled)
                        .filter(p -> soldOut == null || p.soldOut() == soldOut)
                        .toList();

        int startIndex = 0;
        if (cursor != null) {
            UUID afterProductId = ProductCursor.decode(cursor);
            startIndex = indexOf(filtered, afterProductId) + 1;
        }

        int endIndex = Math.min(startIndex + limit, filtered.size());
        List<Product> page = startIndex >= filtered.size() ? List.of() : filtered.subList(startIndex, endIndex);
        boolean hasMore = endIndex < filtered.size();
        String nextCursor = hasMore ? ProductCursor.encode(page.get(page.size() - 1).productId()) : null;

        return new ProductListPage(page, nextCursor, hasMore);
    }

    private static int indexOf(List<Product> products, UUID productId) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).productId().equals(productId)) {
                return i;
            }
        }
        throw new InvalidCursorException(productId.toString());
    }
}
