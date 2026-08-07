package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * A single Argon2id hash generated once at startup and reused for every login attempt against a
 * {@code loginId} that does not exist, so that account-existence cannot be inferred from response timing
 * (ADR-02-006, ADR-02-009).
 */
@Component
public class DummyPasswordHash {

    private final String value;

    public DummyPasswordHash(PasswordEncoder passwordEncoder) {
        this.value = passwordEncoder.encode(UUID.randomUUID() + UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }
}
