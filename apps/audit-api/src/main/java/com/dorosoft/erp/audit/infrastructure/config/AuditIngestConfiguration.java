package com.dorosoft.erp.audit.infrastructure.config;

import com.dorosoft.erp.audit.application.api.ingest.AuditEventContractValidator;
import com.dorosoft.erp.audit.application.api.ingest.IngestAuditEventService;
import com.dorosoft.erp.audit.application.api.ingest.IngestAuditEventUseCase;
import com.dorosoft.erp.audit.application.port.AuditRecordRepositoryPort;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditRetentionProperties.class)
public class AuditIngestConfiguration {

    @Bean
    Clock auditClock() {
        return Clock.systemUTC();
    }

    @Bean
    AuditEventContractValidator auditEventContractValidator() {
        return new AuditEventContractValidator();
    }

    @Bean
    IngestAuditEventUseCase ingestAuditEventUseCase(
            AuditRecordRepositoryPort repository,
            AuditEventContractValidator validator,
            Clock auditClock,
            AuditRetentionProperties retentionProperties) {
        return new IngestAuditEventService(
                repository,
                validator,
                auditClock,
                Duration.ofDays(retentionProperties.days()));
    }
}
