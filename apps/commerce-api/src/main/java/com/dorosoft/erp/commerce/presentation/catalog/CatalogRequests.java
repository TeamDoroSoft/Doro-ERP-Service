package com.dorosoft.erp.commerce.presentation.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Catalog HTTP 요청 계약. 값 형식은 여기에서, 업무 규칙은 Domain에서 검증한다.
 */
public final class CatalogRequests {

    private CatalogRequests() {
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(0) @Max(9999) Integer displayOrder,
            Boolean active) {
    }

    public record UpdateCategoryRequest(
            @Size(max = 100) String name,
            @Min(0) @Max(9999) Integer displayOrder,
            Boolean active) {
    }

    public record CreateProductRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull @Min(0) Long price,
            @NotNull @Min(0) @Max(9999) Integer displayOrder,
            Boolean active) {
    }

    public record UpdateProductRequest(
            UUID categoryId,
            @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(0) Long price,
            @Min(0) @Max(9999) Integer displayOrder,
            Boolean active) {
    }

    public record ChangeSoldOutRequest(@NotNull Boolean soldOut) {
    }
}
