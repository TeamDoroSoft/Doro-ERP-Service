package com.dorosoft.erp.commerce.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 실제 PostgreSQL Container에서 Flyway Migration, Constraint와 Lock 동작을 검증하는 통합 Test 기반.
 *
 * <p>Docker가 없는 환경에서는 실행할 수 없다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = CommercePostgresInitializer.class)
public @interface CommerceIntegrationTest {
}
