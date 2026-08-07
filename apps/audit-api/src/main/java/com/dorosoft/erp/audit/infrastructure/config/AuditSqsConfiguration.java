package com.dorosoft.erp.audit.infrastructure.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementOrdering;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditSqsProperties.class)
public class AuditSqsConfiguration {

    @Bean
    SqsMessageListenerContainerFactory<Object> auditSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient,
            AuditSqsProperties properties) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .pollTimeout(properties.pollTimeout())
                        .maxMessagesPerPoll(properties.maxMessagesPerPoll())
                        .maxConcurrentMessages(properties.maxConcurrentMessages())
                        .acknowledgementMode(AcknowledgementMode.MANUAL)
                        .acknowledgementInterval(Duration.ZERO)
                        .acknowledgementThreshold(0)
                        .acknowledgementOrdering(AcknowledgementOrdering.ORDERED_BY_GROUP)
                        .queueNotFoundStrategy(QueueNotFoundStrategy.FAIL))
                .build();
    }
}
