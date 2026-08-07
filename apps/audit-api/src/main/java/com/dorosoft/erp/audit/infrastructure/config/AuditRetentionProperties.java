package com.dorosoft.erp.audit.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("audit.retention")
public record AuditRetentionProperties(int days) {

    public AuditRetentionProperties {
        if (days <= 0) {
            throw new IllegalArgumentException("audit.retention.days must be positive");
        }
    }
}
