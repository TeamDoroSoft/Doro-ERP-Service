package com.dorosoft.erp.storeaccess.infrastructure.identity.ratelimit;

import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityHmacProperties;
import com.dorosoft.erp.storeaccess.infrastructure.identity.security.HmacDigester;
import org.springframework.stereotype.Component;

/**
 * Builds Redis keys for the account and client-IP login rate limit scopes (ADR-02-007) as an HMAC digest,
 * never the raw {@code tenantCode}/{@code loginId}/IP. Normalizing {@code tenantCode} is feature 01's
 * contract; callers must pass an already-normalized value.
 */
@Component
public class LoginRateLimitKeyFactory {

    private static final String ACCOUNT_KEY_PREFIX = "identity:ratelimit:login:account:";
    private static final String CLIENT_IP_KEY_PREFIX = "identity:ratelimit:login:ip:";

    private final HmacDigester hmacDigester;
    private final IdentityHmacProperties hmacProperties;

    public LoginRateLimitKeyFactory(HmacDigester hmacDigester, IdentityHmacProperties hmacProperties) {
        this.hmacDigester = hmacDigester;
        this.hmacProperties = hmacProperties;
    }

    public String accountKey(String normalizedTenantCode, LoginId loginId) {
        String digest = hmacDigester.digest(
                hmacProperties.rateLimitSecret(), normalizedTenantCode + ":" + loginId.value());
        return ACCOUNT_KEY_PREFIX + digest;
    }

    public String clientIpKey(String clientIp) {
        String digest = hmacDigester.digest(hmacProperties.rateLimitSecret(), clientIp);
        return CLIENT_IP_KEY_PREFIX + digest;
    }
}
