package com.dorosoft.erp.catalog.infrastructure.media;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Catalog Product Media(ADR-007)의 S3 Client·Presigner 조립과 만료 정리 Scheduling 진입점.
 * Application Main을 건드리지 않기 위해 {@code @EnableScheduling}을 이 모듈 전용 Configuration에 둔다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ProductMediaProperties.class)
class CatalogMediaConfig {

    @Bean
    S3Client productMediaS3Client(ProductMediaProperties properties) {
        return S3Client.builder().region(Region.of(properties.region())).build();
    }

    @Bean
    S3Presigner productMediaS3Presigner(ProductMediaProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.region())).build();
    }
}
