package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import com.dorosoft.erp.storeaccess.application.api.identity.AuthProblemCode;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.EmployeeSessionAuthenticationFilter;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.ProblemResponseWriter;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.SessionIdleRenewalInterceptor;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Employee Session authentication and Endpoint authorization (ADR-02-002/003/007/011). Role and Tenant Scope
 * are re-verified inside each Application Service (ADR-02-010); this layer only decides whether a request
 * carries a valid employee Session, matching "Role과 Tenant Scope를 Application 계층에서도 재검증한다".
 *
 * <p>Session persistence is entirely custom ({@link EmployeeSessionAuthenticationFilter} /
 * {@link SessionIdleRenewalInterceptor}), so Spring Security's own context repository and session-fixation
 * machinery — both of which touch {@link jakarta.servlet.http.HttpServletRequest#getSession()} — are turned
 * off via {@code STATELESS} session creation policy and a disabled {@code securityContext()}.
 */
@Configuration
public class SecurityConfig {

    /**
     * Both establish a brand-new Credential rather than acting on an existing one, so neither has a CSRF
     * Cookie/token pair to present yet; every other state-changing Endpoint requires one.
     */
    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/v1/auth/login", "/api/v1/kiosk-auth/activate"
    };

    @Bean
    public EmployeeSessionAuthenticationFilter employeeSessionAuthenticationFilter(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider,
            ProblemResponseWriter problemResponseWriter,
            Clock clock) {
        return new EmployeeSessionAuthenticationFilter(sessionRepositoryProvider, problemResponseWriter, clock);
    }

    @Bean
    public SessionIdleRenewalInterceptor sessionIdleRenewalInterceptor(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider, Clock clock) {
        return new SessionIdleRenewalInterceptor(sessionRepositoryProvider, clock);
    }

    @Bean
    public WebMvcConfigurer identityWebMvcConfigurer(SessionIdleRenewalInterceptor sessionIdleRenewalInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(sessionIdleRenewalInterceptor);
            }
        };
    }

    @Bean
    public SecurityFilterChain identitySecurityFilterChain(
            HttpSecurity http,
            EmployeeSessionAuthenticationFilter employeeSessionAuthenticationFilter,
            ProblemResponseWriter problemResponseWriter) throws Exception {
        http
                .securityContext(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(PUBLIC_POST_ENDPOINTS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                problemResponseWriter.write(request, response, AuthProblemCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                problemResponseWriter.write(request, response, AuthProblemCode.ACCESS_DENIED)))
                .authorizeHttpRequests(authorize -> {
                    for (String path : PUBLIC_POST_ENDPOINTS) {
                        authorize.requestMatchers(HttpMethod.POST, path).permitAll();
                    }
                    authorize
                            .requestMatchers("/actuator/**").permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(employeeSessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
