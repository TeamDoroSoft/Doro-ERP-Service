package com.dorosoft.erp.identity.infrastructure.security;

import com.dorosoft.erp.identity.application.authentication.IdentityDeniedPrivacyAccessHandler;
import com.dorosoft.erp.identity.infrastructure.session.AbsoluteSessionExpirationFilter;
import jakarta.servlet.Filter;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class IdentitySecurityConfiguration {

    @Bean
    SessionCsrfTokenRepository identityCsrfTokenRepository() {
        return new SessionCsrfTokenRepository();
    }

    @Bean
    HostOriginValidationFilter identityHostOriginValidationFilter(
            IdentityWebPolicy webPolicy,
            SecurityProblemWriter problemWriter
    ) {
        return new HostOriginValidationFilter(webPolicy, problemWriter);
    }

    @Bean
    AbsoluteSessionExpirationFilter identityAbsoluteSessionExpirationFilter(
            Clock clock,
            SecurityProblemWriter problemWriter
    ) {
        return new AbsoluteSessionExpirationFilter(clock, problemWriter);
    }

    @Bean
    PasswordChangeRequiredFilter identityPasswordChangeRequiredFilter(SecurityProblemWriter problemWriter) {
        return new PasswordChangeRequiredFilter(problemWriter);
    }

    @Bean
    FilterRegistrationBean<HostOriginValidationFilter> identityHostOriginValidationFilterRegistration(
            HostOriginValidationFilter filter
    ) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<AbsoluteSessionExpirationFilter> identityAbsoluteSessionExpirationFilterRegistration(
            AbsoluteSessionExpirationFilter filter
    ) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<PasswordChangeRequiredFilter> identityPasswordChangeRequiredFilterRegistration(
            PasswordChangeRequiredFilter filter
    ) {
        return disabledFilterRegistration(filter);
    }

    @Bean
    @Order(100)
    SecurityFilterChain identitySecurityFilterChain(
            HttpSecurity http,
            SessionCsrfTokenRepository csrfTokenRepository,
            HostOriginValidationFilter hostOriginValidationFilter,
            AbsoluteSessionExpirationFilter absoluteSessionExpirationFilter,
            PasswordChangeRequiredFilter passwordChangeRequiredFilter,
            SecurityProblemWriter problemWriter,
            IdentityDeniedPrivacyAccessHandler deniedPrivacyAccess
    ) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .securityMatcher("/api/v1/**", "/actuator/**")
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .requireCsrfProtectionMatcher(new IdentityCsrfRequestMatcher()))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new IdentityAuthenticationEntryPoint(problemWriter))
                        .accessDeniedHandler(new IdentityAccessDeniedHandler(problemWriter, deniedPrivacyAccess)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/sessions").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/sessions/current").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/store/availability").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(hostOriginValidationFilter, CsrfFilter.class)
                .addFilterAfter(absoluteSessionExpirationFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(passwordChangeRequiredFilter, AuthorizationFilter.class);
        return http.build();
    }

    private static <T extends Filter> FilterRegistrationBean<T> disabledFilterRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
