package com.dorosoft.erp.audit.infrastructure.persistence;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
class AuditHmacSecretValidator {
    // application.yaml 기본값과 동기화 필요
    static final String DEFAULT_HMAC_SECRET = "local-dev-only-change-me-32bytes-min";

    private final AuditProperties properties;
    private final Environment environment;

    AuditHmacSecretValidator(AuditProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && DEFAULT_HMAC_SECRET.equals(properties.getHmacSecret())) {
            throw new IllegalStateException("prod 프로파일에서는 감사 HMAC secret 기본값을 사용할 수 없다");
        }
    }
}
