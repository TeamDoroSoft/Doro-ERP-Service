package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Batch size for the daily expired local security history cleanup (ADR-02-015). */
@ConfigurationProperties(prefix = "doro.identity.security-history")
public record SecurityHistoryRetentionProperties(int retentionBatchSize) {

    public SecurityHistoryRetentionProperties {
        if (retentionBatchSize <= 0) {
            throw new IllegalArgumentException("retentionBatchSize must be positive");
        }
    }
}
