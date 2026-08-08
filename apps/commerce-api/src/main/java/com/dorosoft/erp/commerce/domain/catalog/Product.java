package com.dorosoft.erp.commerce.domain.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * 판매 상품. 가격·판매 상태·품절 상태의 정본이다 (FR-CATALOG-002, FR-CATALOG-003).
 *
 * <p>품절은 재고 계산 결과가 아니라 직원이 명시적으로 변경하는 상태다.
 */
public final class Product {

    private final UUID id;
    private final UUID tenantId;
    private final UUID categoryId;
    private final String name;
    private final String description;
    private final long price;
    private final boolean soldOut;
    private final CatalogStatus status;
    private final int displayOrder;
    private final long version;

    private Product(
            UUID id,
            UUID tenantId,
            UUID categoryId,
            String name,
            String description,
            long price,
            boolean soldOut,
            CatalogStatus status,
            int displayOrder,
            long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.soldOut = soldOut;
        this.status = status;
        this.displayOrder = displayOrder;
        this.version = version;
    }

    public static Product create(
            UUID id,
            UUID tenantId,
            UUID categoryId,
            String name,
            String description,
            long price,
            CatalogStatus status,
            int displayOrder) {
        return new Product(
                CatalogRules.requireIdentifier(id),
                CatalogRules.requireTenant(tenantId),
                CatalogRules.requireCategory(categoryId),
                CatalogRules.normalizeName(name),
                CatalogRules.normalizeDescription(description),
                CatalogRules.requirePrice(price),
                false,
                CatalogRules.requireStatus(status),
                CatalogRules.requireDisplayOrder(displayOrder),
                0L);
    }

    public static Product restore(
            UUID id,
            UUID tenantId,
            UUID categoryId,
            String name,
            String description,
            long price,
            boolean soldOut,
            CatalogStatus status,
            int displayOrder,
            long version) {
        return new Product(
                CatalogRules.requireIdentifier(id),
                CatalogRules.requireTenant(tenantId),
                CatalogRules.requireCategory(categoryId),
                name,
                description,
                price,
                soldOut,
                CatalogRules.requireStatus(status),
                displayOrder,
                version);
    }

    public Product moveToCategory(UUID newCategoryId) {
        return copyWith(CatalogRules.requireCategory(newCategoryId), name, description, price, soldOut, status,
                displayOrder);
    }

    public Product rename(String newName) {
        return copyWith(categoryId, CatalogRules.normalizeName(newName), description, price, soldOut, status,
                displayOrder);
    }

    public Product describe(String newDescription) {
        return copyWith(categoryId, name, CatalogRules.normalizeDescription(newDescription), price, soldOut, status,
                displayOrder);
    }

    public Product changePrice(long newPrice) {
        return copyWith(categoryId, name, description, CatalogRules.requirePrice(newPrice), soldOut, status,
                displayOrder);
    }

    public Product reorder(int newDisplayOrder) {
        return copyWith(categoryId, name, description, price, soldOut, status,
                CatalogRules.requireDisplayOrder(newDisplayOrder));
    }

    public Product changeStatus(CatalogStatus newStatus) {
        return copyWith(categoryId, name, description, price, soldOut, CatalogRules.requireStatus(newStatus),
                displayOrder);
    }

    public Product changeSoldOut(boolean newSoldOut) {
        return copyWith(categoryId, name, description, price, newSoldOut, status, displayOrder);
    }

    private Product copyWith(
            UUID newCategoryId,
            String newName,
            String newDescription,
            long newPrice,
            boolean newSoldOut,
            CatalogStatus newStatus,
            int newDisplayOrder) {
        return new Product(id, tenantId, newCategoryId, newName, newDescription, newPrice, newSoldOut, newStatus,
                newDisplayOrder, version);
    }

    public boolean belongsTo(UUID candidateTenantId) {
        return tenantId.equals(candidateTenantId);
    }

    /** Category까지 활성이고 품절이 아닐 때만 판매 가능하다. */
    public boolean isSellableUnder(MenuCategory category) {
        return status.isActive() && category != null && category.isActive() && !soldOut;
    }

    public boolean isActive() {
        return status.isActive();
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID categoryId() {
        return categoryId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public long price() {
        return price;
    }

    public boolean soldOut() {
        return soldOut;
    }

    public CatalogStatus status() {
        return status;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Product product && id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
