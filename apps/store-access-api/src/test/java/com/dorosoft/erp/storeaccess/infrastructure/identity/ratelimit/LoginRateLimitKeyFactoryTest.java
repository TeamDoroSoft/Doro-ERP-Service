package com.dorosoft.erp.storeaccess.infrastructure.identity.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityHmacProperties;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.HmacDigester;
import org.junit.jupiter.api.Test;

class LoginRateLimitKeyFactoryTest {

    private final IdentityHmacProperties properties =
            new IdentityHmacProperties("rate-limit-secret", "idempotency-secret", "kiosk-credential-secret");
    private final LoginRateLimitKeyFactory factory = new LoginRateLimitKeyFactory(new HmacDigester(), properties);

    @Test
    void accountKeyIsDeterministicForTheSameTenantAndLoginId() {
        LoginId loginId = LoginId.normalize("owner01");

        String first = factory.accountKey("acme", loginId);
        String second = factory.accountKey("acme", loginId);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void accountKeyDiffersByTenantCode() {
        LoginId loginId = LoginId.normalize("owner01");

        String acme = factory.accountKey("acme", loginId);
        String other = factory.accountKey("other-tenant", loginId);

        assertThat(acme).isNotEqualTo(other);
    }

    @Test
    void accountKeyDoesNotLeakRawTenantCodeOrLoginId() {
        String key = factory.accountKey("acme", LoginId.normalize("owner01"));

        assertThat(key).doesNotContain("acme", "owner01");
    }

    @Test
    void clientIpKeyIsDeterministicAndDoesNotLeakRawIp() {
        String first = factory.clientIpKey("203.0.113.10");
        String second = factory.clientIpKey("203.0.113.10");
        String other = factory.clientIpKey("203.0.113.11");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(other);
        assertThat(first).doesNotContain("203.0.113.10");
    }
}
