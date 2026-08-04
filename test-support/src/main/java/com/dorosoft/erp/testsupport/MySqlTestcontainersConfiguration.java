package com.dorosoft.erp.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Redis 의존성이 없는 모듈(예: modules:catalog, modules:order)이 MySQL만 필요할 때 사용한다. */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }
}
