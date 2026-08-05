package com.dorosoft.erp.catalog.presentation.dto;

import com.dorosoft.erp.catalog.domain.category.Category;
import java.util.UUID;

public record CategoryResponse(UUID categoryId, String name, int displayOrder, long version) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.categoryId(), category.name(), category.displayOrder(), category.version());
    }
}
