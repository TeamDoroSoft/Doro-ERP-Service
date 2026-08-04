package com.dorosoft.erp.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCookieSupportTest {

    @Test
    void issuesHostOnlySecureHttpOnlyStrictNonPersistentCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        SessionCookieSupport.issue(response, "opaque-session_id-123");

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("__Host-ERPSESSION=opaque-session_id-123")
                .contains("Path=/", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain=", "Max-Age=", "Expires=");
    }

    @Test
    void deletionUsesTheSameCookieNameAndPath() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        SessionCookieSupport.clear(response);

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("__Host-ERPSESSION=")
                .contains("Path=/", "Secure", "HttpOnly", "SameSite=Strict", "Max-Age=0")
                .doesNotContain("Domain=");
    }

    @Test
    void rejectsHeaderInjection() {
        assertThatThrownBy(() -> SessionCookieSupport.issue(new MockHttpServletResponse(), "bad\r\nheader"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
