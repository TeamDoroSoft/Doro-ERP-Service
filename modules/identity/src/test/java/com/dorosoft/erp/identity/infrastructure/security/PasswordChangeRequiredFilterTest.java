package com.dorosoft.erp.identity.infrastructure.security;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordChangeRequiredFilterTest {

    private final PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter(
            new SecurityProblemWriter(new ObjectMapper()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsBusinessEndpointForMustChangePasswordPrincipal() throws Exception {
        authenticate(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void permitsPasswordChangeCsrfSelfAndLogoutEndpoints() throws Exception {
        authenticate(true);
        for (String path : Set.of(
                "/api/v1/auth/me",
                "/api/v1/auth/csrf",
                "/api/v1/auth/me/password",
                "/api/v1/auth/sessions/current")) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(path).isNotNull();
        }
    }

    private void authenticate(boolean mustChangePassword) {
        IdentityPrincipal principal = new IdentityPrincipal(UUID.randomUUID(), "store-a", "EMPLOYEE",
                Set.of("order.read"), mustChangePassword);
        SecurityContextHolder.getContext().setAuthentication(new IdentityAuthentication(principal));
    }
}
