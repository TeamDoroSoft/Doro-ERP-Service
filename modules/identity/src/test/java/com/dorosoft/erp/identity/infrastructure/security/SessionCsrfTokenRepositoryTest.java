package com.dorosoft.erp.identity.infrastructure.security;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCsrfTokenRepositoryTest {

    private final SessionCsrfTokenRepository repository = new SessionCsrfTokenRepository();

    @Test
    void generatesBase64UrlEncoded256BitToken() {
        CsrfToken token = repository.generateToken(new MockHttpServletRequest());

        assertThat(token.getHeaderName()).isEqualTo("X-CSRF-TOKEN");
        assertThat(Base64.getUrlDecoder().decode(token.getToken())).hasSize(32);
        assertThat(token.getToken()).doesNotContain("=");
    }

    @Test
    void savesLoadsAndRemovesTokenOnlyFromTheSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);

        assertThat(repository.loadToken(request).getToken()).isEqualTo(token.getToken());
        assertThat(response.getCookies()).isEmpty();

        repository.saveToken(null, request, response);
        assertThat(repository.loadToken(request)).isNull();
    }
}
