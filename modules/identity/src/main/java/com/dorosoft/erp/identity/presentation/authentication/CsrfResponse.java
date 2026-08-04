package com.dorosoft.erp.identity.presentation.authentication;

public record CsrfResponse(String csrfToken) {
    public CsrfResponse {
        if (csrfToken == null || csrfToken.isBlank()) {
            throw new IllegalArgumentException("csrfToken must not be blank");
        }
    }

    @Override
    public String toString() {
        return "CsrfResponse[csrfToken=redacted]";
    }
}
