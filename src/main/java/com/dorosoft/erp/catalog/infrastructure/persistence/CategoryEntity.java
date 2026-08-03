package com.dorosoft.erp.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "category")
class CategoryEntity {

    @Id
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "catalog_id", nullable = false)
    private UUID catalogId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CategoryEntity() {}

    CategoryEntity(UUID categoryId, UUID catalogId, String name, int displayOrder) {
        this.categoryId = categoryId;
        this.catalogId = catalogId;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    UUID getCategoryId() {
        return categoryId;
    }

    UUID getCatalogId() {
        return catalogId;
    }

    String getName() {
        return name;
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    long getVersion() {
        return version;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void rename(String name) {
        this.name = name;
    }

    void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
