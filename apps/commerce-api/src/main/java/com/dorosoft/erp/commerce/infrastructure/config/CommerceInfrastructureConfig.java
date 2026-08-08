package com.dorosoft.erp.commerce.infrastructure.config;

import com.dorosoft.erp.commerce.infrastructure.security.ActorContextProperties;
import com.dorosoft.erp.platform.web.GlobalProblemAdvice;
import com.dorosoft.erp.platform.web.RequestIdFilter;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Commerce Infrastructure 공통 Bean.
 *
 * <p>{@code platform:web}은 Auto-Configuration을 제공하지 않으므로 공통 Request ID Filter와
 * Problem 계약 Advice를 이 App에서 명시적으로 등록한다.
 */
@Configuration
@EnableConfigurationProperties(ActorContextProperties.class)
@Import({RequestIdFilter.class, GlobalProblemAdvice.class})
public class CommerceInfrastructureConfig {

    /** 발생 시각과 서명 만료 계산을 테스트에서 고정할 수 있도록 Clock을 주입한다. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
