package com.dorosoft.erp.order.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** OrderItem Aggregate 자식. 주문 시점 선택 옵션명·추가 금액 Snapshot. */
@Entity
@Table(name = "order_item_option")
class OrderItemOptionEntity {

    @Id
    @Column(name = "order_item_option_id", nullable = false)
    private UUID orderItemOptionId;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(name = "option_order", nullable = false)
    private int optionOrder;

    @Column(name = "option_name", nullable = false, length = 100)
    private String optionName;

    @Column(name = "additional_price", nullable = false)
    private long additionalPrice;

    protected OrderItemOptionEntity() {}

    OrderItemOptionEntity(
            UUID orderItemOptionId, UUID optionId, int optionOrder, String optionName, long additionalPrice) {
        this.orderItemOptionId = orderItemOptionId;
        this.optionId = optionId;
        this.optionOrder = optionOrder;
        this.optionName = optionName;
        this.additionalPrice = additionalPrice;
    }

    UUID getOrderItemOptionId() {
        return orderItemOptionId;
    }

    UUID getOptionId() {
        return optionId;
    }

    String getOptionName() {
        return optionName;
    }

    long getAdditionalPrice() {
        return additionalPrice;
    }
}
