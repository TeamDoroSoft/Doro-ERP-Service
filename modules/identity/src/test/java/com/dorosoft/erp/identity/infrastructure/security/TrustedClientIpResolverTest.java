package com.dorosoft.erp.identity.infrastructure.security;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedClientIpResolverTest {

    @Test
    void ignoresUntrustedForwardingHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.20");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(new TrustedClientIpResolver().resolve(request).bytes())
                .containsExactly(InetAddress.ofLiteral("192.0.2.20").getAddress());
    }
}
