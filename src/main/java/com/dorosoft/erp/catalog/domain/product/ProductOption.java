package com.dorosoft.erp.catalog.domain.product;

import java.util.Objects;
import java.util.UUID;

/** Product Aggregate 안의 옵션명, 추가 금액, 활성 상태와 표시 순서(FR-MENU-004). */
public record ProductOption(UUID optionId, String name, Money additionalPrice, boolean enabled, int displayOrder) {

    private static final int NAME_MAX_LENGTH = 100;

    public ProductOption {
        Objects.requireNonNull(optionId, "optionId는 필수다");
        name = requireValidName(name);
        if (additionalPrice.amount() < 0) {
            throw new InvalidPriceException("옵션 추가 금액", additionalPrice.amount());
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder는 0 이상이어야 한다");
        }
    }

    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "옵션 name은 필수다");
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("옵션 name은 공백 제거 후 1~" + NAME_MAX_LENGTH + "자여야 한다");
        }
        return trimmed;
    }
}
