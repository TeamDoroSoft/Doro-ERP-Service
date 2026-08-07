package com.dorosoft.erp.audit.domain.record;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SensitiveMetadataPolicy {

    private static final Set<String> EXACT_FORBIDDEN_KEYS = Set.of(
            "authorization",
            "cookie",
            "paymentkey",
            "phone",
            "phonenumber",
            "idempotencykey",
            "requestbody",
            "responsebody");

    private SensitiveMetadataPolicy() {}

    public static void rejectForbiddenKeys(Map<String, Object> metadata) {
        inspectMap(metadata);
    }

    private static void inspectMap(Map<?, ?> values) {
        values.forEach((key, value) -> {
            if (key != null && isForbidden(key.toString())) {
                throw new IllegalArgumentException("metadata contains a forbidden key");
            }
            inspectValue(value);
        });
    }

    private static void inspectValue(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            inspectMap(nestedMap);
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(SensitiveMetadataPolicy::inspectValue);
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                inspectValue(element);
            }
        }
    }

    private static boolean isForbidden(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return EXACT_FORBIDDEN_KEYS.contains(normalized)
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("session")
                || normalized.contains("csrf");
    }
}
