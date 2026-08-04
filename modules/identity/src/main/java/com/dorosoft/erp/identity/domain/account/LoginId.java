package com.dorosoft.erp.identity.domain.account;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 업체 Schema 안에서 비교하는 정규화 로그인 ID. */
public record LoginId(String value) {
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9._-]{4,50}$");

    public LoginId {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("login ID must match the normalized login ID contract");
        }
    }

    public static LoginId normalize(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new LoginId(raw.strip().toLowerCase(Locale.ROOT));
    }

    public static LoginId fromNormalized(String normalized) {
        return new LoginId(normalized);
    }
}
