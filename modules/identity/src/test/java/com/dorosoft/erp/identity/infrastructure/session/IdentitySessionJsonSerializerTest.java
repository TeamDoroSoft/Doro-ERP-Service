package com.dorosoft.erp.identity.infrastructure.session;

import com.dorosoft.erp.identity.infrastructure.security.IdentityAuthentication;
import com.dorosoft.erp.identity.infrastructure.security.IdentityPrincipal;
import com.dorosoft.erp.identity.infrastructure.security.SessionCsrfTokenRepository;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentitySessionJsonSerializerTest {

    private final IdentitySessionJsonSerializer serializer = new IdentitySessionJsonSerializer();

    @Test
    void roundTripsOnlyTheIdentitySecuritySnapshotWithoutCredentials() {
        UUID accountId = UUID.randomUUID();
        IdentityPrincipal principal = new IdentityPrincipal(accountId, "store-a", "EMPLOYEE",
                Set.of("order.read", "order.create"), true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new IdentityAuthentication(principal));

        byte[] serialized = serializer.serialize(context);
        SecurityContext restored = (SecurityContext) serializer.deserialize(serialized);
        IdentityAuthentication authentication = (IdentityAuthentication) restored.getAuthentication();

        assertThat(authentication.getPrincipal()).isEqualTo(principal);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(new String(serialized, StandardCharsets.UTF_8))
                .contains(accountId.toString(), "store-a", "EMPLOYEE", "order.read")
                .doesNotContain("password", "credential", "contact");
    }

    @Test
    void roundTripsCsrfAndTimeValues() {
        CsrfToken token = SessionCsrfTokenRepository.token("csrf-value");

        assertThat(((CsrfToken) serializer.deserialize(serializer.serialize(token))).getToken())
                .isEqualTo("csrf-value");
        assertThat(serializer.deserialize(serializer.serialize(1234L))).isEqualTo(1234L);
    }

    @Test
    void rejectsTypesOutsideTheAllowlist() {
        assertThatThrownBy(() -> serializer.serialize(new Object()))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    void rejectsUnknownJsonDiscriminatorInsteadOfUsingPolymorphicTyping() {
        byte[] payload = "{\"type\":\"java.lang.Runtime\",\"value\":{}}"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(payload))
                .isInstanceOf(SerializationException.class);
    }
}
