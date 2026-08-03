package com.dorosoft.erp.catalog.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 상품 Aggregate Root의 JPA 매핑. 옵션 자식 컬렉션을 소유한다.
 * updatedAt은 {@code @UpdateTimestamp}를 쓰지 않고 {@link #touch()}로 명시적으로 갱신한다.
 * Hibernate는 OneToMany 자식 컬렉션만 바뀐 경우 부모 Version을 자동으로 올려주지 않으므로,
 * 옵션만 바뀐 저장에서도 반드시 touch()를 호출해 Product version이 오르게 한다(FR-MENU-004).
 */
@Entity
@Table(name = "product")
class ProductEntity {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "catalog_id", nullable = false)
    private UUID catalogId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "base_price", nullable = false)
    private long basePrice;

    @Column(name = "image_alt_text", length = 200)
    private String imageAltText;

    @Column(name = "sales_enabled", nullable = false)
    private boolean salesEnabled;

    @Column(name = "sold_out", nullable = false)
    private boolean soldOut;

    @Column(name = "stock_managed", nullable = false)
    private boolean stockManaged;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "idempotency_request_hash", length = 64)
    private String idempotencyRequestHash;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "product_id", nullable = false)
    private List<ProductOptionEntity> options = new ArrayList<>();

    protected ProductEntity() {}

    ProductEntity(
            UUID productId,
            UUID catalogId,
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
            String idempotencyKey,
            String idempotencyRequestHash) {
        this.productId = productId;
        this.catalogId = catalogId;
        this.categoryId = categoryId;
        this.mediaId = mediaId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.imageAltText = imageAltText;
        this.salesEnabled = salesEnabled;
        this.soldOut = soldOut;
        this.stockManaged = stockManaged;
        this.displayOrder = displayOrder;
        this.idempotencyKey = idempotencyKey;
        this.idempotencyRequestHash = idempotencyRequestHash;
        this.updatedAt = Instant.now();
    }

    void applyBasicInfo(
            UUID categoryId,
            UUID mediaId,
            String name,
            String description,
            long basePrice,
            String imageAltText,
            boolean salesEnabled,
            boolean stockManaged,
            int displayOrder) {
        this.categoryId = categoryId;
        this.mediaId = mediaId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.imageAltText = imageAltText;
        this.salesEnabled = salesEnabled;
        this.stockManaged = stockManaged;
        this.displayOrder = displayOrder;
    }

    /** 값 변경 없이도 Row를 Dirty로 만들어 저장 시 @Version(version)이 증가하게 한다(옵션만 바뀐 경우 포함). */
    void touch() {
        this.updatedAt = Instant.now();
    }

    void replaceOptions(List<ProductOptionEntity> replacements) {
        this.options.clear();
        this.options.addAll(replacements);
    }

    UUID getProductId() {
        return productId;
    }

    UUID getCatalogId() {
        return catalogId;
    }

    UUID getCategoryId() {
        return categoryId;
    }

    UUID getMediaId() {
        return mediaId;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    long getBasePrice() {
        return basePrice;
    }

    String getImageAltText() {
        return imageAltText;
    }

    boolean isSalesEnabled() {
        return salesEnabled;
    }

    boolean isSoldOut() {
        return soldOut;
    }

    boolean isStockManaged() {
        return stockManaged;
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

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    String getIdempotencyRequestHash() {
        return idempotencyRequestHash;
    }

    List<ProductOptionEntity> getOptions() {
        return options;
    }
}
