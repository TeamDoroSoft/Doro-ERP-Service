package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.domain.product.ProductOption;
import java.util.UUID;

public record ProductOptionResponse(UUID optionId, String name, long additionalPrice, boolean enabled, int displayOrder) {

    public static ProductOptionResponse from(ProductOption option) {
        return new ProductOptionResponse(
                option.optionId(), option.name(), option.additionalPrice().amount(), option.enabled(), option.displayOrder());
    }
}
