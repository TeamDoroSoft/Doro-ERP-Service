package com.dorosoft.erp.storeaccess.infrastructure.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dorosoft.erp.storeaccess.domain.identity.Role;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.EmployeeAuthenticationToken;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.EmployeePrincipal;
import com.dorosoft.erp.storeaccess.application.api.identity.UserActivity;
import com.dorosoft.erp.storeaccess.infrastructure.identity.session.EmployeeSessionAttributes;
import com.dorosoft.erp.storeaccess.infrastructure.identity.session.SessionPrincipalIndexKey;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies {@link SecurityConfig}'s Session authentication end to end against real Redis: unauthenticated
 * rejection, valid-Session acceptance, 12-hour absolute expiration, and that only {@link UserActivity}
 * endpoints renew the 30-minute idle timeout (ADR-02-002/003).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class SecurityConfigIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("doro.identity.hmac.rate-limit-secret", () -> "test-rate-limit-secret");
        registry.add("doro.identity.hmac.idempotency-secret", () -> "test-idempotency-secret");
        registry.add("doro.identity.hmac.kiosk-credential-secret", () -> "test-kiosk-credential-secret");
    }

    @TestConfiguration
    static class TestProtectedEndpoints {

        @RestController
        static class ProtectedTestController {

            @UserActivity
            @GetMapping("/api/v1/_test/user-activity")
            ResponseEntity<Void> userActivity() {
                return ResponseEntity.ok().build();
            }

            @GetMapping("/api/v1/_test/passive")
            ResponseEntity<Void> passive() {
                return ResponseEntity.ok().build();
            }
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisIndexedSessionRepository sessionRepository;

    @Autowired
    private HttpSessionIdResolver sessionIdResolver;

    @Autowired
    private Clock clock;

    @Test
    void unauthenticatedRequestToAProtectedEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/_test/passive"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicLoginPathIsNotBlockedBySecurity() throws Exception {
        // No body: fails request validation (400), not Security (401) — proves permitAll, now that a real
        // AuthController exists to validate against.
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithAValidSessionCookieIsAuthenticated() throws Exception {
        RedisIndexedSessionRepository.RedisSession session = createAuthenticatedSession(Role.OWNER, clock.instant().plus(Duration.ofHours(12)));

        mockMvc.perform(get("/api/v1/_test/passive").cookie(sessionCookie(session.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithAnAbsolutelyExpiredSessionIsRejectedAndTheSessionIsDeleted() throws Exception {
        RedisIndexedSessionRepository.RedisSession session =
                createAuthenticatedSession(Role.OWNER, clock.instant().minus(Duration.ofSeconds(1)));

        mockMvc.perform(get("/api/v1/_test/passive").cookie(sessionCookie(session.getId())))
                .andExpect(status().isUnauthorized());

        assertThat(sessionRepository.findById(session.getId())).isNull();
    }

    @Test
    void aUserActivityEndpointRenewsTheIdleTimeoutButAPassiveEndpointDoesNot() throws Exception {
        RedisIndexedSessionRepository.RedisSession activitySession =
                createAuthenticatedSession(Role.OWNER, clock.instant().plus(Duration.ofHours(12)));
        RedisIndexedSessionRepository.RedisSession passiveSession =
                createAuthenticatedSession(Role.OWNER, clock.instant().plus(Duration.ofHours(12)));
        // Reload once before acting so both "original" timestamps have already been through the same
        // Redis round-trip (millisecond-precision storage) as the "reloaded" ones compared at the end —
        // otherwise comparing an in-memory Instant against a round-tripped one is a precision mismatch,
        // not a real behavioral difference.
        Instant originalActivityLastAccessed = sessionRepository.findById(activitySession.getId()).getLastAccessedTime();
        Instant originalPassiveLastAccessed = sessionRepository.findById(passiveSession.getId()).getLastAccessedTime();
        Thread.sleep(50);

        mockMvc.perform(get("/api/v1/_test/user-activity").cookie(sessionCookie(activitySession.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/_test/passive").cookie(sessionCookie(passiveSession.getId())))
                .andExpect(status().isOk());

        Session reloadedActivitySession = sessionRepository.findById(activitySession.getId());
        Session reloadedPassiveSession = sessionRepository.findById(passiveSession.getId());
        assertThat(reloadedActivitySession.getLastAccessedTime()).isAfter(originalActivityLastAccessed);
        assertThat(reloadedPassiveSession.getLastAccessedTime()).isEqualTo(originalPassiveLastAccessed);
    }

    @Test
    void aRequestWithNoSessionCookieIsTreatedAsAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/_test/passive"))
                .andExpect(status().isUnauthorized());
    }

    /** Encodes a raw Session ID exactly as {@link HttpSessionIdResolver} would write it into a Cookie. */
    private Cookie sessionCookie(String sessionId) {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        sessionIdResolver.setSessionId(mockRequest, mockResponse, sessionId);
        return mockResponse.getCookie("SESSION");
    }

    private RedisIndexedSessionRepository.RedisSession createAuthenticatedSession(Role role, Instant absoluteExpiresAt) {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        EmployeePrincipal principal = new EmployeePrincipal(tenantId, UUID.randomUUID(), employeeId, role, "owner01");
        EmployeeAuthenticationToken authentication = new EmployeeAuthenticationToken(principal);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);

        RedisIndexedSessionRepository.RedisSession session = sessionRepository.createSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                new SessionPrincipalIndexKey(tenantId, employeeId).value());
        session.setAttribute(EmployeeSessionAttributes.ABSOLUTE_EXPIRES_AT, absoluteExpiresAt);
        sessionRepository.save(session);
        return session;
    }
}
