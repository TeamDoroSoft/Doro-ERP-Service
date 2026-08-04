package com.dorosoft.erp.identity.presentation.common;

import java.util.Objects;

/** Feature 01 success response envelope. */
public record ApiEnvelope<T>(T data, String requestId) {
    public ApiEnvelope {
        Objects.requireNonNull(data, "data");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    public static <T> ApiEnvelope<T> of(T data, String requestId) {
        return new ApiEnvelope<>(data, requestId);
    }
}
