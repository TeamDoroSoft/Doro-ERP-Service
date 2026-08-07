package com.dorosoft.erp.audit.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("audit.sqs")
public record AuditSqsProperties(
        String queueName,
        Duration pollTimeout,
        int maxMessagesPerPoll,
        int maxConcurrentMessages) {

    public AuditSqsProperties {
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("audit.sqs.queue-name is required");
        }
        if (pollTimeout == null || pollTimeout.isZero() || pollTimeout.isNegative()
                || pollTimeout.compareTo(Duration.ofSeconds(20)) > 0) {
            throw new IllegalArgumentException("audit.sqs.poll-timeout must be between 1ms and 20s");
        }
        if (maxMessagesPerPoll < 1 || maxMessagesPerPoll > 10) {
            throw new IllegalArgumentException("audit.sqs.max-messages-per-poll must be between 1 and 10");
        }
        if (maxConcurrentMessages < 1) {
            throw new IllegalArgumentException("audit.sqs.max-concurrent-messages must be positive");
        }
    }
}
