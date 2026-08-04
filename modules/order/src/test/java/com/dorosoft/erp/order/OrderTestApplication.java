package com.dorosoft.erp.order;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * modules:order는 독립된 Spring Boot Application을 갖지 않으므로, 통합 테스트 전용 부트스트랩 지점을 둔다.
 * order는 catalog(카탈로그 조회·Product/Category)에 의존하므로 두 패키지를 함께 스캔한다.
 * JPA 엔티티·리포지토리 스캔은 scanBasePackages와 별도 메커니즘이라 명시적으로 지정한다.
 */
@SpringBootApplication(scanBasePackages = {"com.dorosoft.erp.order", "com.dorosoft.erp.catalog"})
@EntityScan(basePackages = {"com.dorosoft.erp.order", "com.dorosoft.erp.catalog"})
@EnableJpaRepositories(basePackages = {"com.dorosoft.erp.order", "com.dorosoft.erp.catalog"})
class OrderTestApplication {
}
