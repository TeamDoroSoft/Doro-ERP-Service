package com.dorosoft.erp.catalog.infrastructure.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Catalog가 5.17 중앙 Audit 모듈에 필요한 배포 설정값을 조립하는 진입점.
 *
 * <p>modules:catalog는 {@code spring-boot-starter-json}/{@code -web}을 두지 않아 Spring이
 * {@code ObjectMapper} Bean을 자동 구성하지 않는다(기존 {@code CatalogAuditValues}도 그래서 직접
 * {@code new}한다). apps/erp-api처럼 이미 Jackson Auto Configuration이 있는 실행 환경에서는 그 Bean을
 * 그대로 쓰고, modules:catalog 단독 테스트 Context처럼 없는 경우에만 이 fallback을 쓴다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogAuditTenantProperties.class)
class CatalogAuditConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper catalogAuditObjectMapper() {
        return new ObjectMapper();
    }
}
