package com.dorosoft.erp.catalog;

import com.dorosoft.erp.testsupport.RealAuditWriterTestConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * modules:catalog는 독립된 Spring Boot Application을 갖지 않으므로, 통합 테스트 전용 부트스트랩
 * 지점을 둔다. Catalog는 5.17 중앙 Audit 모듈의 실제 {@code AuditWriter}가 Context에 있어야 하므로
 * {@link RealAuditWriterTestConfiguration}을 가져온다.
 */
@SpringBootApplication
@Import(RealAuditWriterTestConfiguration.class)
class CatalogTestApplication {
}
