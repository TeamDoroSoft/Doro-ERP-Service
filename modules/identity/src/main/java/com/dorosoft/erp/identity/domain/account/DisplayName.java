package com.dorosoft.erp.identity.domain.account;

import java.util.Objects;

/** 직원 표시명. API 계약에 따라 앞뒤 공백을 제거한 1~100자 값을 유지한다. */
public record DisplayName(String value) {
    public DisplayName {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > 100 || !value.equals(value.strip())) {
            throw new IllegalArgumentException("display name must be a stripped value of 1 to 100 characters");
        }
    }

    public static DisplayName normalize(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new DisplayName(raw.strip());
    }

    public static DisplayName of(String normalized) {
        return new DisplayName(normalized);
    }
}
