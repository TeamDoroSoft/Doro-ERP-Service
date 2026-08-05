package com.dorosoft.erp.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * TABLE-00/TABLE-10: 멱등 응답 암호화 Key가 구성되지 않았거나 형식이 잘못된 채로 운영(prod) 환경이
 * 기동되지 않도록 막는다. Key가 없는 개발/로컬 환경에서는 애플리케이션 기동 자체는 허용하되, 실제로
 * Table 멱등 저장·재생 기능을 사용하는 순간에만 {@code TableIdempotencyResponseCrypto}가 안전하게
 * 실패한다(see {@link TableRuntimeConfiguration}).
 */
@Component
public class TableSecurityPropertiesValidator {

    private final TableSecurityProperties tableSecurityProperties;
    private final Environment environment;

    public TableSecurityPropertiesValidator(
            TableSecurityProperties tableSecurityProperties, Environment environment) {
        this.tableSecurityProperties = tableSecurityProperties;
        this.environment = environment;
    }

    @PostConstruct
    void validateOperationalConfiguration() {
        String encryptionKeyBase64 = tableSecurityProperties.getIdempotency().getEncryptionKeyBase64();
        boolean productionProfile =
                Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (!StringUtils.hasText(encryptionKeyBase64)) {
            require(!productionProfile,
                    "table.security.idempotency.encryption-key-base64 must be set in production");
            return;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encryptionKeyBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "table.security.idempotency.encryption-key-base64 must be valid Base64", exception);
        }
        require(decoded.length >= 32,
                "table.security.idempotency.encryption-key-base64 must decode to at least 32 bytes");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
