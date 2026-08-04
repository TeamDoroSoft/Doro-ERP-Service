package com.dorosoft.erp.order.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Order Aggregate 자식. 주문 시점 상품명·가격·옵션·수량·금액 Snapshot을 담는다. */
@Entity
@Table(name = "order_item")
class OrderItemEntity {

    @Id
    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "client_line_id", nullable = false, length = 50)
    private String clientLineId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "base_unit_price", nullable = false)
    private long baseUnitPrice;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_amount", nullable = false)
    private long lineAmount;

    @Column(name = "stock_managed", nullable = false)
    private boolean stockManaged;

    @Column(name = "catalog_revision", nullable = false)
    private long catalogRevision;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_item_id", nullable = false)
    private List<OrderItemOptionEntity> options = new ArrayList<>();

    protected OrderItemEntity() {}

    OrderItemEntity(
            UUID orderItemId,
            String clientLineId,
            UUID productId,
            String productName,
            long baseUnitPrice,
            long unitPrice,
            int quantity,
            long lineAmount,
            boolean stockManaged,
            long catalogRevision) {
        this.orderItemId = orderItemId;
        this.clientLineId = clientLineId;
        this.productId = productId;
        this.productName = productName;
        this.baseUnitPrice = baseUnitPrice;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineAmount = lineAmount;
        this.stockManaged = stockManaged;
        this.catalogRevision = catalogRevision;
    }

    void replaceOptions(List<OrderItemOptionEntity> replacements) {
        this.options.clear();
        this.options.addAll(replacements);
    }

    UUID getOrderItemId() {
        return orderItemId;
    }

    UUID getProductId() {
        return productId;
    }

    String getClientLineId() {
        return clientLineId;
    }

    String getProductName() {
        return productName;
    }

    long getBaseUnitPrice() {
        return baseUnitPrice;
    }

    long getUnitPrice() {
        return unitPrice;
    }

    int getQuantity() {
        return quantity;
    }

    long getLineAmount() {
        return lineAmount;
    }

    boolean isStockManaged() {
        return stockManaged;
    }

    long getCatalogRevision() {
        return catalogRevision;
    }

    List<OrderItemOptionEntity> getOptions() {
        return options;
    }
}
