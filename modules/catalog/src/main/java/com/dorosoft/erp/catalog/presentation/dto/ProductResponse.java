package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.domain.product.Product;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID productId,
        UUID categoryId,
        UUID mediaId,
        String name,
        String description,
        long basePrice,
        String imageAltText,
        boolean salesEnabled,
        boolean soldOut,
        boolean stockManaged,
        int displayOrder,
        long version,
        List<ProductOptionResponse> options) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.productId(),
                product.categoryId(),
                product.mediaId(),
                product.name(),
                product.description(),
                product.basePrice().amount(),
                product.imageAltText(),
                product.salesEnabled(),
                product.soldOut(),
                product.stockManaged(),
                product.displayOrder(),
                product.version(),
                product.options().stream().map(ProductOptionResponse::from).toList());
    }
}
