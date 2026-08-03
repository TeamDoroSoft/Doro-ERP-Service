package com.dorosoft.erp.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Product Aggregate 자식. 독립 Version이 없고 부모(ProductEntity)의 version이 옵션 변경도 함께 지킨다. */
@Entity
@Table(name = "product_option")
class ProductOptionEntity {

    @Id
    @Column(name = "product_option_id", nullable = false)
    private UUID productOptionId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "additional_price", nullable = false)
    private long additionalPrice;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductOptionEntity() {}

    ProductOptionEntity(UUID productOptionId, String name, long additionalPrice, boolean enabled, int displayOrder) {
        this.productOptionId = productOptionId;
        this.name = name;
        this.additionalPrice = additionalPrice;
        this.enabled = enabled;
        this.displayOrder = displayOrder;
    }

    UUID getProductOptionId() {
        return productOptionId;
    }

    String getName() {
        return name;
    }

    long getAdditionalPrice() {
        return additionalPrice;
    }

    boolean isEnabled() {
        return enabled;
    }

    int getDisplayOrder() {
        return displayOrder;
    }
}
