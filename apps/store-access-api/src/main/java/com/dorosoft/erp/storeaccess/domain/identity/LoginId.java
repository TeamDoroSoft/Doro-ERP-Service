package com.dorosoft.erp.storeaccess.domain.identity;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized employee login identifier, unique within a tenant (ADR-02-008).
 * Only the normalized form is retained; the original display value is not kept.
 */
public record LoginId(String value) {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 50;

    public LoginId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "loginId length must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isAllowedChar(c)) {
                throw new IllegalArgumentException(
                        "loginId may only contain lowercase letters, digits, '.', '_' and '-'");
            }
        }
        if (!isAlphanumeric(value.charAt(0)) || !isAlphanumeric(value.charAt(value.length() - 1))) {
            throw new IllegalArgumentException("loginId must start and end with a letter or digit");
        }
    }

    /** Trims ASCII whitespace and lowercases with {@link Locale#ROOT} before validating the result. */
    public static LoginId normalize(String rawValue) {
        Objects.requireNonNull(rawValue, "rawValue must not be null");
        return new LoginId(rawValue.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isAllowedChar(char c) {
        return isAlphanumeric(c) || c == '.' || c == '_' || c == '-';
    }

    private static boolean isAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
