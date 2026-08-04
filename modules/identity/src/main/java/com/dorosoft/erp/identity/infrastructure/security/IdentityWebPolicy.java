package com.dorosoft.erp.identity.infrastructure.security;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Type-safe application-provided boundary values; this module does not bind app configuration. */
public record IdentityWebPolicy(URI publicBaseUrl, List<URI> allowedOrigins) {

    public IdentityWebPolicy {
        Objects.requireNonNull(publicBaseUrl, "publicBaseUrl must not be null");
        allowedOrigins = List.copyOf(Objects.requireNonNull(allowedOrigins, "allowedOrigins must not be null"));
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("allowedOrigins must not be empty");
        }
    }
}
