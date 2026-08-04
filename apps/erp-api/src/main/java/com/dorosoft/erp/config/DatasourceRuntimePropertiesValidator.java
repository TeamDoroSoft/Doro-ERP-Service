package com.dorosoft.erp.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DatasourceRuntimePropertiesValidator {

    private final DatasourceRuntimeProperties properties;
    private final ObjectProvider<JdbcConnectionDetails> connectionDetails;

    public DatasourceRuntimePropertiesValidator(
            DatasourceRuntimeProperties properties,
            ObjectProvider<JdbcConnectionDetails> connectionDetails
    ) {
        this.properties = properties;
        this.connectionDetails = connectionDetails;
    }

    @PostConstruct
    void validate() {
        JdbcConnectionDetails details = connectionDetails.getIfAvailable();
        // PropertiesJdbcConnectionDetails는 package-private이라 이름으로만 구분한다.
        // Testcontainers @ServiceConnection 등 외부 제공 ConnectionDetails가 있으면
        // spring.datasource.* 리터럴 값이 비어 있어도 정상 동작이므로 검증을 건너뛴다.
        boolean usingLiteralDatasourceProperties =
                details == null || "PropertiesJdbcConnectionDetails".equals(details.getClass().getSimpleName());
        if (!usingLiteralDatasourceProperties) {
            return;
        }
        require(StringUtils.hasText(properties.getUrl()), "spring.datasource.url must not be blank");
        require(StringUtils.hasText(properties.getUsername()), "spring.datasource.username must not be blank");
        require(StringUtils.hasText(properties.getPassword()), "spring.datasource.password must not be blank");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
