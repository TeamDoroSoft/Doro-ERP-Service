package com.dorosoft.erp.order.domain.item;

import java.util.UUID;

/** 주문 시점 선택 옵션의 이름·추가 금액 Snapshot(06 공통 주문 관리 Catalog Snapshot). */
public record OrderItemOption(UUID optionId, String optionName, long additionalPrice) {}
