package com.dorosoft.erp.commerce.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Commerce 통합 Test가 공유하는 PostgreSQL Container.
 *
 * <p>Container는 JVM당 한 번만 기동하고 Flyway가 Schema를 만든다. Hibernate는 {@code validate}로만 확인한다.
 * Docker가 없는 환경에서는 이 Initializer를 사용하는 Test를 실행할 수 없다.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class CommercePostgresInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.withDatabaseName("commerce_db");
        POSTGRES.start();
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TestPropertyValues.of(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.flyway.enabled=true",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "doro.commerce.actor-context.signature-required=false")
                .applyTo(applicationContext.getEnvironment());
    }
}
