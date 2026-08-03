package com.dorosoft.erp.audit.infrastructure.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "doro.audit")
public class AuditProperties {
    private String hmacSecret;

    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
}
