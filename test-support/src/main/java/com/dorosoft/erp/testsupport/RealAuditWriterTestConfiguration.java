package com.dorosoft.erp.testsupport;

import com.dorosoft.erp.audit.application.api.AuditWriter;
import com.dorosoft.erp.audit.application.port.AuditAppendPort;
import com.dorosoft.erp.audit.application.port.AuditPayloadSigner;
import com.dorosoft.erp.audit.application.usecase.DefaultAuditWriter;
import com.dorosoft.erp.audit.infrastructure.HmacSha256AuditPayloadSigner;
import com.dorosoft.erp.audit.infrastructure.JdbcAuditRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * 5.17 중앙 Audit 모듈(기능 17)의 실제 {@code AuditWriter} Bean 조립을 테스트 전용 HMAC 키로 재현한다.
 * apps/erp-api의 {@code IdentityRuntimeConfiguration}이 운영에서 하는 것과 같은 역할이다. modules:catalog를
 * 의존하는 모듈(catalog 자신, order 등)은 각자의 테스트 부트스트랩에 {@code @Import}해서 쓴다 — catalog의
 * Bean들은 실제 {@code AuditWriter}가 Spring Context에 있어야 생성되기 때문이다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RealAuditWriterTestConfiguration {

    private static final byte[] TEST_HMAC_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuditAppendPort auditAppendPort(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcAuditRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    AuditPayloadSigner auditPayloadSigner() {
        return new HmacSha256AuditPayloadSigner(TEST_HMAC_KEY);
    }

    @Bean
    AuditWriter auditWriter(
            AuditAppendPort appendPort, AuditPayloadSigner payloadSigner, ObjectMapper objectMapper, Clock clock) {
        return new DefaultAuditWriter(appendPort, payloadSigner, objectMapper, clock);
    }
}
