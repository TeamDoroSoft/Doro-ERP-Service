package com.dorosoft.erp.commerce.domain.catalog;

import java.util.Objects;
import java.util.UUID;

/**
 * 메뉴 분류. 상태 변경만 허용하며 물리 삭제 개념을 두지 않는다 (FR-CATALOG-001).
 */
public final class MenuCategory {

    private final UUID id;
    private final UUID tenantId;
    private final String name;
    private final int displayOrder;
    private final CatalogStatus status;
    private final long version;

    private MenuCategory(UUID id, UUID tenantId, String name, int displayOrder, CatalogStatus status, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.displayOrder = displayOrder;
        this.status = status;
        this.version = version;
    }

    public static MenuCategory create(UUID id, UUID tenantId, String name, int displayOrder, CatalogStatus status) {
        return new MenuCategory(
                CatalogRules.requireIdentifier(id),
                CatalogRules.requireTenant(tenantId),
                CatalogRules.normalizeName(name),
                CatalogRules.requireDisplayOrder(displayOrder),
                CatalogRules.requireStatus(status),
                0L);
    }

    public static MenuCategory restore(
            UUID id, UUID tenantId, String name, int displayOrder, CatalogStatus status, long version) {
        return new MenuCategory(
                CatalogRules.requireIdentifier(id),
                CatalogRules.requireTenant(tenantId),
                name,
                displayOrder,
                CatalogRules.requireStatus(status),
                version);
    }

    public MenuCategory rename(String newName) {
        return new MenuCategory(id, tenantId, CatalogRules.normalizeName(newName), displayOrder, status, version);
    }

    public MenuCategory reorder(int newDisplayOrder) {
        return new MenuCategory(
                id, tenantId, name, CatalogRules.requireDisplayOrder(newDisplayOrder), status, version);
    }

    public MenuCategory changeStatus(CatalogStatus newStatus) {
        return new MenuCategory(id, tenantId, name, displayOrder, CatalogRules.requireStatus(newStatus), version);
    }

    public boolean belongsTo(UUID candidateTenantId) {
        return tenantId.equals(candidateTenantId);
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

    public String name() {
        return name;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public CatalogStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof MenuCategory category && id.equals(category.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
