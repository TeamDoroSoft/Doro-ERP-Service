package com.dorosoft.erp.commerce.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.commerce.application.api.security.ActorContextHolder;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * SOFT-435: Commerce가 전달된 Actor Context를 무조건 신뢰하지 않는지 검증한다.
 */
class ActorContextFilterTest {

    private static final String SECRET = "test-shared-actor-context-secret";
    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @AfterEach
    void tearDown() {
        ActorContextHolder.clear();
    }

    @Test
    void validSignatureIsAccepted() throws Exception {
        MockHttpServletRequest request = signedRequest(String.valueOf(NOW.toEpochMilli()), true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.tenantId).isEqualTo(TENANT);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(ActorContextHolder.current()).isEmpty();
    }

    @Test
    void forgedSignatureIsRejected() throws Exception {
        MockHttpServletRequest request = signedRequest(String.valueOf(NOW.toEpochMilli()), true);
        request.removeHeader(ActorContextFilter.HEADER_SIGNATURE);
        request.addHeader(ActorContextFilter.HEADER_SIGNATURE, "forged-signature");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("AUTHENTICATION_REQUIRED");
    }

    @Test
    void missingSignatureIsRejectedWhenVerificationIsRequired() throws Exception {
        MockHttpServletRequest request = signedRequest(String.valueOf(NOW.toEpochMilli()), false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void expiredSignatureIsRejected() throws Exception {
        MockHttpServletRequest request =
                signedRequest(String.valueOf(NOW.minusSeconds(3600).toEpochMilli()), true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void deviceRoleWithEmployeeActorTypeIsRejected() throws Exception {
        String issuedAt = String.valueOf(NOW.toEpochMilli());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/catalog/menu");
        request.addHeader(ActorContextFilter.HEADER_TENANT_ID, TENANT.toString());
        request.addHeader(ActorContextFilter.HEADER_ACTOR_TYPE, "EMPLOYEE");
        request.addHeader(ActorContextFilter.HEADER_ACTOR_ID, ACTOR.toString());
        request.addHeader(ActorContextFilter.HEADER_ACTOR_ROLE, "KIOSK_DEVICE");
        request.addHeader(ActorContextFilter.HEADER_ISSUED_AT, issuedAt);
        request.addHeader(
                ActorContextFilter.HEADER_SIGNATURE,
                hmac(String.join("\n", TENANT.toString(), "", "EMPLOYEE", ACTOR.toString(), "KIOSK_DEVICE", issuedAt)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void requestWithoutActorHeadersReachesApplicationWithoutContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/catalog/menu");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.tenantId).isNull();
    }

    @Test
    void startupFailsWhenSignatureIsRequiredButSecretIsMissing() {
        ActorContextProperties properties = new ActorContextProperties();
        properties.setSignatureRequired(true);
        properties.setSecret(" ");

        ActorContextFilter filter =
                new ActorContextFilter(properties, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(filter::verifyConfiguration).isInstanceOf(IllegalStateException.class);
    }

    private ActorContextFilter filter() {
        ActorContextProperties properties = new ActorContextProperties();
        properties.setSignatureRequired(true);
        properties.setSecret(SECRET);
        ActorContextFilter filter =
                new ActorContextFilter(properties, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        filter.verifyConfiguration();
        return filter;
    }

    private static MockHttpServletRequest signedRequest(String issuedAt, boolean includeSignature) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/catalog/menu");
        request.addHeader(ActorContextFilter.HEADER_TENANT_ID, TENANT.toString());
        request.addHeader(ActorContextFilter.HEADER_ACTOR_TYPE, "EMPLOYEE");
        request.addHeader(ActorContextFilter.HEADER_ACTOR_ID, ACTOR.toString());
        request.addHeader(ActorContextFilter.HEADER_ACTOR_ROLE, "OWNER");
        request.addHeader(ActorContextFilter.HEADER_ISSUED_AT, issuedAt);
        if (includeSignature) {
            request.addHeader(
                    ActorContextFilter.HEADER_SIGNATURE,
                    hmac(String.join("\n", TENANT.toString(), "", "EMPLOYEE", ACTOR.toString(), "OWNER", issuedAt)));
        }
        return request;
    }

    private static String hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class CapturingFilterChain extends MockFilterChain {
        private boolean invoked;
        private UUID tenantId;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            invoked = true;
            tenantId = ActorContextHolder.current().map(context -> context.tenantId()).orElse(null);
        }
    }
}
