package com.dorosoft.erp.table.infrastructure.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class TableManagementSecurityConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain tableManagementSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/tables", "/tables/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/tables", "/tables/**")
                                        .hasAnyAuthority("ROLE_OWNER", "ROLE_MANAGER", "ROLE_ADMIN", "table.manage")
                                        .anyRequest()
                                        .permitAll())
                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain tableQrPublicAccessSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/qr/table-access")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .requestCache(cache -> cache.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
