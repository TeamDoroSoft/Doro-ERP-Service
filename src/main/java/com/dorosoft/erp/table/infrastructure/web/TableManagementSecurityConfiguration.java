package com.dorosoft.erp.table.infrastructure.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class TableManagementSecurityConfiguration {

    @Bean
    SecurityFilterChain tableManagementSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/tables", "/tables/**")
                                        .hasAnyRole("OWNER", "MANAGER", "ADMIN")
                                        .anyRequest()
                                        .permitAll())
                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .build();
    }
}
