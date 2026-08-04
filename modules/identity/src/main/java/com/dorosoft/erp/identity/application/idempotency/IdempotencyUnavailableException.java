package com.dorosoft.erp.identity.application.idempotency;

/** Safe internal signal mapped to the public IDEMPOTENCY_UNAVAILABLE problem. */
public final class IdempotencyUnavailableException extends RuntimeException {
    public IdempotencyUnavailableException(String message) {
        super(message);
    }

    public IdempotencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
