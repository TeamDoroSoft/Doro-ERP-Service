package com.dorosoft.erp.catalog.domain.product;

import java.util.UUID;

/** 옵션 전체 교체 요청 한 줄. optionId가 null이면 새 옵션이다(API 명세: 새 Option은 optionId를 생략한다). */
public record ProductOptionRequest(UUID optionId, String name, long additionalPrice, boolean enabled) {}
