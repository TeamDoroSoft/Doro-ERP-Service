package com.dorosoft.erp.identity.infrastructure.ratelimit;

import java.util.regex.Pattern;

public record IdentityRedisNamespace(String value) {

    private static final Pattern VALID_NAMESPACE = Pattern.compile("^erp:[^:\\s]+:[^:\\s]+:identity$");

    public IdentityRedisNamespace {
        if (value == null || !VALID_NAMESPACE.matcher(value).matches()) {
            throw new IllegalArgumentException("Identity Redis namespace is invalid");
        }
    }

    String rateLimitPrefix() {
        return value + ":rate-limit";
    }
}
