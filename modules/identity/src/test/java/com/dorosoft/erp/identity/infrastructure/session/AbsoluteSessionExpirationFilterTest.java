package com.dorosoft.erp.identity.infrastructure.session;

import com.dorosoft.erp.identity.infrastructure.security.SecurityProblemWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbsoluteSessionExpirationFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");
    private final AbsoluteSessionExpirationFilter filter = new AbsoluteSessionExpirationFilter(
            Clock.fixed(NOW, ZoneOffset.UTC), new SecurityProblemWriter(new ObjectMapper()));

    @Test
    void capsIdleTimeoutAtOneHourWithoutExtendingAbsoluteExpiry() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(IdentitySessionAttributes.ABSOLUTE_EXPIRES_AT,
                NOW.plusSeconds(3_599).plusMillis(900).toEpochMilli());
        MockHttpServletRequest request = request(session, "/api/v1/auth/me");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.getMaxInactiveInterval()).isEqualTo(3_599);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void expiresAtTheEightHourBoundaryAndClearsTheCookie() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(IdentitySessionAttributes.ABSOLUTE_EXPIRES_AT, NOW.toEpochMilli());
        MockHttpServletRequest request = request(session, "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("__Host-ERPSESSION=")
                .contains("Path=/", "Secure", "HttpOnly", "SameSite=Strict", "Max-Age=0")
                .doesNotContain("Domain=");
        assertThatThrownBy(() -> session.getAttribute("anything")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void staleSessionDoesNotBlockAReplacementLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(IdentitySessionAttributes.ABSOLUTE_EXPIRES_AT, NOW.minusSeconds(1).toEpochMilli());
        MockHttpServletRequest request = request(session, "/api/v1/auth/sessions");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    private MockHttpServletRequest request(MockHttpSession session, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setSession(session);
        request.setAttribute("doro.erp.requestId", "req-test");
        return request;
    }
}
