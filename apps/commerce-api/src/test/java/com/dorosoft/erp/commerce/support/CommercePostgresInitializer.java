package com.dorosoft.erp.commerce.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Commerce 통합 Test가 공유하는 PostgreSQL Container.
 *
 * <p>Container는 JVM당 한 번만 기동하고 Flyway가 Schema를 만든다. Hibernate는 {@code validate}로만 확인한다.
 */
public class CommercePostgresInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("commerce_db");

    static {
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
