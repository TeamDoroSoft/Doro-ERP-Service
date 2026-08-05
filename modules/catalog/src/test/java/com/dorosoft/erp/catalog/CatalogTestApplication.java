package com.dorosoft.erp.catalog;

import com.dorosoft.erp.testsupport.RealAuditWriterTestConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * modules:catalog는 독립된 Spring Boot Application을 갖지 않으므로, 통합 테스트 전용 부트스트랩
 * 지점을 둔다. Catalog는 5.17 중앙 Audit 모듈의 실제 {@code AuditWriter}가 Context에 있어야 하므로
 * {@link RealAuditWriterTestConfiguration}을 가져온다. {@code @EnableMethodSecurity}는 Controller의
 * {@code @PreAuthorize}를 테스트에서도 실제로 적용하기 위함이며, apps/erp-api의 전체 HttpSecurity
 * Filter Chain(CSRF·세션 등)은 이 모듈 테스트 범위 밖이라 가져오지 않는다. modules:identity가
 * classpath에 있어 자동 활성화되는 SessionDataRedisAutoConfiguration은 이 모듈 테스트에는 Redis
 * Container가 없어 test/resources/application.yaml의 spring.autoconfigure.exclude로 제외한다
 * (implementation 의존이라 compile 시점엔 클래스 자체가 보이지 않아 여기서 직접 참조할 수 없다).
 * scanBasePackages에 platform.web을 더하는 이유는 apps/erp-api(최상위 com.dorosoft.erp 패키지)와
 * 달리 이 클래스는 com.dorosoft.erp.catalog에 있어 기본 스캔 범위가 platform:web의 RequestIdFilter·
 * GlobalProblemAdvice까지 닿지 않기 때문이다.
 */
@SpringBootApplication(scanBasePackages = {"com.dorosoft.erp.catalog", "com.dorosoft.erp.platform.web"})
@Import(RealAuditWriterTestConfiguration.class)
@EnableMethodSecurity
class CatalogTestApplication {
}
