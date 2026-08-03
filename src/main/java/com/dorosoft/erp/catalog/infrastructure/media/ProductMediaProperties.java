package com.dorosoft.erp.catalog.infrastructure.media;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 배포당 하나의 업체만 서비스하므로(ADR-007) tenantId는 요청 파라미터가 아니라 배포 설정값이다.
 * bucket은 {@code dorosoft-product-media-{env}} 형식을 환경별로 그대로 넣는다.
 */
@Validated
@ConfigurationProperties(prefix = "doro.catalog.media")
public record ProductMediaProperties(
        @NotBlank String tenantId,
        @NotBlank String bucket,
        @NotBlank String region,
        @NotBlank String cloudFrontDomain) {}
