package com.dorosoft.erp.identity.application.authentication;

/** Deployment-bound tenant identity. It is never accepted from an HTTP request body. */
public record IdentityTenant(String value) {
    public IdentityTenant {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tenant must not be blank");
        }
    }
}
