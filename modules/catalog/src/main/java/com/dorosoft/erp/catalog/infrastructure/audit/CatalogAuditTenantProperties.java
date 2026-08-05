package com.dorosoft.erp.catalog.infrastructure.audit;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 배포당 하나의 업체만 서비스하므로(ADR-007과 같은 전제) 감사 기록의 tenantId도 배포 설정값이다. */
@Validated
@ConfigurationProperties(prefix = "doro.erp")
public record CatalogAuditTenantProperties(@NotBlank String tenantId) {}
