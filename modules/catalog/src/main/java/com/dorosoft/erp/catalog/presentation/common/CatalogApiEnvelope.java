package com.dorosoft.erp.catalog.presentation.common;

import java.util.Objects;

/** Feature 03 성공 응답 봉투. */
public record CatalogApiEnvelope<T>(T data, String requestId) {
    public CatalogApiEnvelope {
        Objects.requireNonNull(data, "data");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    public static <T> CatalogApiEnvelope<T> of(T data, String requestId) {
        return new CatalogApiEnvelope<>(data, requestId);
    }
}
