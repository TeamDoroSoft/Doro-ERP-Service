package com.dorosoft.erp.shared.security;

import com.dorosoft.erp.platform.web.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** STORE-02 임시 인증 스텁이며 Identity 모듈이 실제 세션 인증(Redis 기반)을 구현하면 교체·삭제 대상이다. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestIdFilter requestIdFilter,
            StubActorAuthenticationFilter stubActorAuthenticationFilter,
            DoroAuthenticationEntryPoint authenticationEntryPoint,
            DoroAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        // ADR-005 실제 세션 인증 구현 시 Redis 기반 CSRF Token 계약으로 교체 예정
        http.csrf(csrf -> csrf.disable());
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/store-settings")
                .hasAuthority("store.settings.read")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/store-settings/profile")
                .hasAuthority("store.settings.update")
                .anyRequest()
                .permitAll());
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        http.addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(stubActorAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
