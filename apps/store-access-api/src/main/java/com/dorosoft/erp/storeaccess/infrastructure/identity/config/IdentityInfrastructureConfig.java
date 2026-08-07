package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import com.dorosoft.erp.storeaccess.application.api.identity.SecurityHistoryRetentionService;
import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

@Configuration
@EnableConfigurationProperties({
        IdentityPasswordProperties.class, IdentityHmacProperties.class, IdentityRateLimitProperties.class,
        SecurityHistoryRetentionProperties.class})
public class IdentityInfrastructureConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder(IdentityPasswordProperties properties) {
        return new Argon2PasswordEncoder(
                properties.saltLength(),
                properties.hashLength(),
                properties.parallelism(),
                properties.memoryKib(),
                properties.iterations());
    }

    /**
     * Spring Boot does not auto-configure this bean from {@code spring.session.data.redis.*} alone; the
     * employee Session cookie's {@code HttpOnly}/{@code Secure}/{@code SameSite} attributes are defined here
     * (기술 설계.md § 보안) since {@link com.dorosoft.erp.storeaccess.infrastructure.identity.security.SpringSessionEstablisherAdapter}
     * writes the cookie directly, bypassing {@code HttpServletRequest#getSession()} and therefore Spring
     * Session's own auto-cookie-writing filter.
     */
    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        cookieSerializer.setCookieName("SESSION");
        cookieSerializer.setUseHttpOnlyCookie(true);
        cookieSerializer.setUseSecureCookie(true);
        cookieSerializer.setSameSite("Lax");
        CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(cookieSerializer);
        return resolver;
    }

    @Bean
    public SecurityHistoryRetentionService securityHistoryRetentionService(
            EmployeeSecurityHistoryRepository repository, SecurityHistoryRetentionProperties properties, Clock clock) {
        return new SecurityHistoryRetentionService(repository, clock, properties.retentionBatchSize());
    }
}
