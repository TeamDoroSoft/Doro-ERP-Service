package com.dorosoft.erp.identity.infrastructure.security;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class HostOriginValidationFilterTest {

    private final HostOriginValidationFilter filter = new HostOriginValidationFilter(
            new IdentityWebPolicy(
                    URI.create("https://api.store-a.example.com"),
                    java.util.List.of(URI.create("https://store-a.example.com"))),
            new SecurityProblemWriter(new ObjectMapper()));

    @Test
    void acceptsConfiguredHostAndOrigin() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader("Origin", "https://store-a.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void loginStillRequiresOrigin() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.setRequestURI("/api/v1/auth/sessions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_TOKEN_INVALID");
    }

    @Test
    void rejectsUnconfiguredOriginBeforeTheController() throws Exception {
        MockHttpServletRequest request = request("PATCH");
        request.addHeader("Origin", "https://attacker.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsHostMismatchAndDoesNotUseForwardedHost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Host", "other-store.example.com");
        request.addHeader("X-Forwarded-Host", "api.store-a.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/employees");
        request.addHeader("Host", "api.store-a.example.com");
        request.setAttribute("doro.erp.requestId", "req-test");
        return request;
    }
}
