package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Argon2id parameters for employee password hashing (ADR-02-009). */
@ConfigurationProperties(prefix = "doro.identity.password")
public record IdentityPasswordProperties(
        int memoryKib,
        int iterations,
        int parallelism,
        int saltLength,
        int hashLength) {

    public IdentityPasswordProperties {
        if (memoryKib <= 0 || iterations <= 0 || parallelism <= 0 || saltLength <= 0 || hashLength <= 0) {
            throw new IllegalArgumentException("Argon2id parameters must be positive");
        }
    }
}
