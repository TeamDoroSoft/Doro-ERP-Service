package com.dorosoft.erp.commerce.application.api.catalog;

import java.util.UUID;

/**
 * Catalog Use Case 입력 계약. 부분 수정은 {@code null}을 "변경 없음"으로 해석한다.
 */
public final class CatalogCommands {

    private CatalogCommands() {
    }

    public record CreateCategoryCommand(String name, Integer displayOrder, Boolean active) {
    }

    public record UpdateCategoryCommand(String name, Integer displayOrder, Boolean active) {
    }

    public record CreateProductCommand(
            UUID categoryId,
            String name,
            String description,
            Long price,
            Integer displayOrder,
            Boolean active) {
    }

    public record UpdateProductCommand(
            UUID categoryId,
            String name,
            String description,
            Long price,
            Integer displayOrder,
            Boolean active) {
    }

    public record ChangeSoldOutCommand(Boolean soldOut) {
    }
}
