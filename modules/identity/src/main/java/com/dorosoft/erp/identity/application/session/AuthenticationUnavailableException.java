package com.dorosoft.erp.identity.application.session;

/**
 * Stable, non-sensitive signal that the Redis-backed authentication boundary is unavailable.
 */
public final class AuthenticationUnavailableException extends RuntimeException {

    public AuthenticationUnavailableException() {
        super("Authentication infrastructure is unavailable");
    }

    public AuthenticationUnavailableException(Throwable cause) {
        super("Authentication infrastructure is unavailable", cause);
    }
}
