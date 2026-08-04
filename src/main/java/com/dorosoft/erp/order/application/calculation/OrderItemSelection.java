package com.dorosoft.erp.order.application.calculation;

import java.util.List;
import java.util.UUID;

/** 주문 금액 계산 요청의 한 줄: 상품, 선택 옵션, 수량. */
public record OrderItemSelection(UUID productId, List<UUID> optionIds, int quantity) {

    public OrderItemSelection {
        optionIds = optionIds == null ? List.of() : List.copyOf(optionIds);
    }
}
