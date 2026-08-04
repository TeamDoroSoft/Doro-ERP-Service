package com.dorosoft.erp.order.application.calculation;

import com.dorosoft.erp.order.domain.item.InvalidOrderItemsException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 주문 금액 계산 요청의 한 줄: 요청 내 고유 Line ID, 상품, 선택 옵션, 수량. */
public record OrderItemSelection(String clientLineId, UUID productId, List<UUID> optionIds, int quantity) {

    public OrderItemSelection {
        if (optionIds == null) {
            optionIds = List.of();
        } else {
            if (optionIds.stream().anyMatch(Objects::isNull)) {
                throw new InvalidOrderItemsException("optionId는 필수입니다");
            }
            optionIds = List.copyOf(optionIds);
        }
    }
}
