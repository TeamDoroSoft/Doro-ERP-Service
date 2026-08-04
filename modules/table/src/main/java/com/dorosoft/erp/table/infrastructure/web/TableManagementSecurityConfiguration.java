package com.dorosoft.erp.table.infrastructure.web;

import com.dorosoft.erp.table.application.TableOperationObservability;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class TableManagementSecurityConfiguration {

    private static final String REQUEST_ID_ATTRIBUTE = "doro.erp.requestId";

    @Bean
    @Order(0)
    SecurityFilterChain tableManagementSecurityFilterChain(
            HttpSecurity http,
            TableOperationObservability observability,
            @Value("${doro.erp.tenant-id:local-store}") String tenantId) throws Exception {
        return http.securityMatcher("/tables", "/tables/**")
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(
                        exception ->
                                exception
                                        .authenticationEntryPoint(
                                                (request, response, authException) -> {
                                                    recordDenied(
                                                            observability,
                                                            request,
                                                            tenantId,
                                                            "AUTHENTICATION_REQUIRED");
                                                    response.sendError(HttpStatus.UNAUTHORIZED.value());
                                                })
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) -> {
                                                    recordDenied(observability, request, tenantId, "FORBIDDEN");
                                                    response.sendError(HttpStatus.FORBIDDEN.value());
                                                }))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.POST, "/tables/*/sessions")
                                        .hasAnyAuthority(
                                                "ROLE_OWNER",
                                                "ROLE_MANAGER",
                                                "ROLE_ADMIN",
                                                "ROLE_STAFF",
                                                "table.session.manage")
                                        .requestMatchers(HttpMethod.POST, "/tables/*/sessions/*/close")
                                        .hasAnyAuthority(
                                                "ROLE_OWNER",
                                                "ROLE_MANAGER",
                                                "ROLE_ADMIN",
                                                "ROLE_STAFF",
                                                "table.session.manage")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/tables/*/sessions/current/orders",
                                                "/tables/*/sessions/history",
                                                "/tables/*/sessions/*/orders")
                                        .hasAnyAuthority(
                                                "ROLE_OWNER",
                                                "ROLE_MANAGER",
                                                "ROLE_ADMIN",
                                                "ROLE_STAFF",
                                                "table.order.read")
                                        .requestMatchers("/tables", "/tables/**")
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

    private static void recordDenied(
            TableOperationObservability observability,
            HttpServletRequest request,
            String tenantId,
            String reason) {
        observability.failure(
                classifyOperation(request),
                reason,
                tenantId,
                null,
                requestId(request),
                classifyTargetType(request),
                null);
    }

    private static String classifyOperation(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && path.matches("/tables/[^/]+/sessions")) {
            return "session.start";
        }
        if ("POST".equals(method) && path.matches("/tables/[^/]+/sessions/[^/]+/close")) {
            return "session.close";
        }
        if ("POST".equals(method) && path.matches("/tables/[^/]+/qr-credentials")) {
            return "qr.issue";
        }
        if ("POST".equals(method) && path.matches("/tables/[^/]+/qr-credentials/reissue")) {
            return "qr.reissue";
        }
        if ("POST".equals(method) && "/tables".equals(path)) {
            return "table.create";
        }
        if ("PUT".equals(method) && path.matches("/tables/[^/]+")) {
            return "table.update";
        }
        if ("PATCH".equals(method) && path.matches("/tables/[^/]+/activation")) {
            return "table.activation";
        }
        if ("GET".equals(method) && path.contains("/orders")) {
            return "table.order.read";
        }
        return "table.request";
    }

    private static String classifyTargetType(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/qr-credentials")) {
            return "TABLE_QR_CREDENTIAL";
        }
        if (path.contains("/sessions")) {
            return "TABLE_SESSION";
        }
        return "TABLE";
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String text && !text.isBlank()) {
            return text;
        }
        return "none";
    }
}
