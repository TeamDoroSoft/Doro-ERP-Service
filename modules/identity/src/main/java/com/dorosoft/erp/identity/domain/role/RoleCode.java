package com.dorosoft.erp.identity.domain.role;

import java.util.Objects;
import java.util.regex.Pattern;

/** 최대 40자의 역할 자연 키. */
public record RoleCode(String value) {
    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");

    public RoleCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("role code must contain 1 to 40 uppercase identifier characters");
        }
    }

    public boolean isAdmin() {
        return "ADMIN".equals(value);
    }
}
