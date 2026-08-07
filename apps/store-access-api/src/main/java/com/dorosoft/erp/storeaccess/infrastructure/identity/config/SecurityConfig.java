package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import com.dorosoft.erp.storeaccess.application.api.identity.AuthProblemCode;
import com.dorosoft.erp.storeaccess.application.api.identity.KioskAuthenticationService;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.EmployeeSessionAuthenticationFilter;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.KioskDeviceAuthenticationFilter;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.ProblemResponseWriter;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.SessionIdleRenewalInterceptor;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Employee Session and Kiosk Cookie authentication and Endpoint authorization
 * (ADR-02-002/003/007/011/013). Role and Tenant Scope are re-verified inside each Application Service
 * (ADR-02-010); this layer only decides whether a request carries a valid employee Session or Kiosk Cookie,
 * matching "Role과 Tenant Scope를 Application 계층에서도 재검증한다".
 *
 * <p>Session persistence is entirely custom ({@link EmployeeSessionAuthenticationFilter} /
 * {@link SessionIdleRenewalInterceptor}), so Spring Security's own context repository and Request Cache are
 * disabled. {@code sessionCreationPolicy(NEVER)} is used rather than {@code STATELESS}: STATELESS's own
 * Session-management handling was found to silently invalidate the very Sessions this Config's Filter had
 * just authenticated/renewed on the same request (reproduced by a Redis-backed Session vanishing immediately
 * after a successful, authenticated response) — NEVER avoids that without reintroducing any Spring
 * Security-managed Session creation.
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

    /**
     * Spring Session auto-registers two beans that back {@code HttpServletRequest#getSession()} with this
     * same Redis store: {@code springSessionRepositoryFilter} (the {@code SessionRepositoryFilter} itself,
     * from Spring Session core) and Spring Boot's {@code sessionRepositoryFilterRegistration} (a
     * {@code DelegatingFilterProxyRegistrationBean} wrapping it for the Servlet container) — neither has a
     * config property or {@code @ConditionalOnMissingBean} escape hatch, and Filter-discovery infrastructure
     * (confirmed with MockMvc) can pick up the Filter bean directly, bypassing the registration wrapper
     * entirely, so both must be removed. Left in place, the moment anything elsewhere in the Servlet/MVC
     * stack calls {@code getSession()}, it silently touches/renews the Session's {@code lastAccessedTime} —
     * exactly the "any access extends idle timeout" behavior ADR-02-003 requires NOT happen outside Endpoints
     * explicitly marked {@link com.dorosoft.erp.storeaccess.application.api.identity.UserActivity}
     * (confirmed by reproducing a passive request's Session gaining a fresh {@code lastAccessedTime} it
     * should not have). Removing both bean definitions by name — as a {@code static}
     * {@link BeanFactoryPostProcessor} bean method, so it runs before either definition is processed — is the
     * only available way to stop this.
     */
    @Bean
    static BeanFactoryPostProcessor removeAutoSessionRepositoryFilterRegistration() {
        return beanFactory -> {
            Logger log = LoggerFactory.getLogger(SecurityConfig.class);
            for (String beanName : new String[] {"sessionRepositoryFilterRegistration", "springSessionRepositoryFilter"}) {
                if (beanFactory instanceof BeanDefinitionRegistry registry && registry.containsBeanDefinition(beanName)) {
                    registry.removeBeanDefinition(beanName);
                    log.info("Removed Spring Boot's auto-registered {} to keep employee Session idle renewal opt-in", beanName);
                }
            }
        };
    }

    @Bean
    public EmployeeSessionAuthenticationFilter employeeSessionAuthenticationFilter(
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider,
            HttpSessionIdResolver sessionIdResolver,
            ProblemResponseWriter problemResponseWriter,
            Clock clock) {
        return new EmployeeSessionAuthenticationFilter(
                sessionRepositoryProvider, sessionIdResolver, problemResponseWriter, clock);
    }

    /**
     * Any {@code Filter}-typed {@code @Bean} is, by default, ALSO auto-registered by Spring Boot as its own
     * standalone Servlet Filter — entirely separate from {@code .addFilterBefore(employeeSessionAuthenticationFilter, ...)}
     * below, which wires the SAME bean into the Security chain. That standalone registration runs at a
     * different position (outside {@code springSecurityFilterChain}), so this Filter's own
     * {@code OncePerRequestFilter} guard does not reliably prevent it from resolving/touching the Session a
     * second time. Disabling the auto-registration (the standard Spring Security + Boot pattern for Filter
     * beans meant only for {@code HttpSecurity}) leaves the {@code .addFilterBefore} wiring as the only path.
     */
    @Bean
    public FilterRegistrationBean<EmployeeSessionAuthenticationFilter> employeeSessionAuthenticationFilterRegistration(
            EmployeeSessionAuthenticationFilter filter) {
        FilterRegistrationBean<EmployeeSessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public KioskDeviceAuthenticationFilter kioskDeviceAuthenticationFilter(
            KioskAuthenticationService kioskAuthenticationService, ProblemResponseWriter problemResponseWriter) {
        return new KioskDeviceAuthenticationFilter(kioskAuthenticationService, problemResponseWriter);
    }

    /** See {@link #employeeSessionAuthenticationFilterRegistration} — the same Boot-generic-Filter-bean
     * auto-registration must be disabled here too, leaving only the {@code .addFilterAfter} wiring below. */
    @Bean
    public FilterRegistrationBean<KioskDeviceAuthenticationFilter> kioskDeviceAuthenticationFilterRegistration(
            KioskDeviceAuthenticationFilter filter) {
        FilterRegistrationBean<KioskDeviceAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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
            KioskDeviceAuthenticationFilter kioskDeviceAuthenticationFilter,
            ProblemResponseWriter problemResponseWriter) throws Exception {
        http
                .securityContext(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .requestCache(cache -> cache.disable())
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
                .addFilterBefore(employeeSessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(kioskDeviceAuthenticationFilter, EmployeeSessionAuthenticationFilter.class);
        return http.build();
    }
}
