package com.dorosoft.erp.identity.presentation.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dorosoft.erp.identity.application.authentication.CurrentIdentitySessionService;
import com.dorosoft.erp.identity.application.authentication.LoginAuthenticationService;
import com.dorosoft.erp.identity.application.authentication.LoginResult;
import com.dorosoft.erp.identity.application.authentication.IdentityClientAddressResolver;
import com.dorosoft.erp.identity.application.ratelimit.ClientIpAddress;
import com.dorosoft.erp.identity.infrastructure.security.SessionCookieSupport;
import com.dorosoft.erp.identity.infrastructure.security.SessionCsrfTokenRepository;
import com.dorosoft.erp.identity.infrastructure.security.IdentityAuthenticatedActorResolver;
import com.dorosoft.erp.identity.infrastructure.security.IdentityAuthentication;
import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.identity.infrastructure.security.ServletIdentitySessionCookieAdapter;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class AuthenticationControllerTest {
    private final LoginAuthenticationService loginService = mock(LoginAuthenticationService.class);
    private final CurrentIdentitySessionService currentSessionService = mock(CurrentIdentitySessionService.class);
    private final IdentityClientAddressResolver ipResolver = mock(IdentityClientAddressResolver.class);
    private final SessionCsrfTokenRepository csrfRepository = mock(SessionCsrfTokenRepository.class);
    private AuthenticationController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthenticationController(
                loginService, currentSessionService, ipResolver, csrfRepository,
                new ServletIdentitySessionCookieAdapter(), new IdentityAuthenticatedActorResolver());
    }

    @Test
    void loginReturns201NoStoreAndOnlyPutsTheOpaqueIdInTheSecureCookie() {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(ipResolver.resolve(request)).thenReturn(ClientIpAddress.of(InetAddress.ofLiteral("192.0.2.10")));
        when(ipResolver.resolveLiteral(request)).thenReturn("192.0.2.10");
        when(loginService.login(any())).thenReturn(new LoginResult(
                UUID.randomUUID(), "직원 1", "EMPLOYEE", Set.of("order.read"), false,
                "opaque-session", "csrf", 3600, Instant.parse("2026-08-04T11:00:00Z")));

        var result = controller.login(new LoginRequest("staff01", "secret"), request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeader("Set-Cookie")).isEqualTo(
                "__Host-ERPSESSION=opaque-session; Path=/; Secure; HttpOnly; SameSite=Strict");
        assertThat(result.getBody().data().toString()).doesNotContain("opaque-session");
    }

    @Test
    void staleLogoutIs204AndAlwaysClearsTheCookie() {
        MockHttpServletRequest request = request();
        request.setCookies(new jakarta.servlet.http.Cookie(SessionCookieSupport.COOKIE_NAME, "stale-session"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.logout(null, request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        assertThat(result.getBody()).isNull();
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        verify(currentSessionService).logout("stale-session", null, "req-1");
    }

    @Test
    void csrfReturnsTheExistingSessionBoundTokenWithNoStore() {
        MockHttpServletRequest request = request();
        request.setSession(new MockHttpSession());
        when(csrfRepository.loadToken(request)).thenReturn(SessionCsrfTokenRepository.token("csrf"));
        var principal = new IdentityPrincipal(
                UUID.randomUUID(), "tenant-a", "EMPLOYEE", Set.of("order.read"), false);

        var result = controller.csrf(new IdentityAuthentication(principal), request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(result.getBody().data().csrfToken()).isEqualTo("csrf");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("doro.erp.requestId", "req-1");
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
