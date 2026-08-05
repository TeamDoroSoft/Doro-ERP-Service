package com.dorosoft.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** TABLE-00/TABLE-10 멱등 응답 암호화 Key 바인딩. 원문 Key 값은 이 타입 밖으로 노출되지 않는다. */
@ConfigurationProperties(prefix = "table.security")
@Validated
public class TableSecurityProperties {

    private final Idempotency idempotency = new Idempotency();

    public Idempotency getIdempotency() {
        return idempotency;
    }

    public static final class Idempotency {
        private String encryptionKeyBase64 = "";

        public String getEncryptionKeyBase64() {
            return encryptionKeyBase64;
        }

        public void setEncryptionKeyBase64(String encryptionKeyBase64) {
            this.encryptionKeyBase64 = encryptionKeyBase64;
        }
    }
}
