package com.dorosoft.erp.order.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** 주문 Aggregate Root의 JPA 매핑. Item Snapshot 자식 컬렉션을 소유한다. */
@Entity
@Table(name = "orders")
class OrderEntity {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("lineOrder ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {}

    OrderEntity(UUID orderId, long totalAmount, String currency) {
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.currency = currency;
    }

    void replaceItems(List<OrderItemEntity> replacements) {
        this.items.clear();
        this.items.addAll(replacements);
    }

    UUID getOrderId() {
        return orderId;
    }

    long getTotalAmount() {
        return totalAmount;
    }

    String getCurrency() {
        return currency;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    List<OrderItemEntity> getItems() {
        return items;
    }
}
