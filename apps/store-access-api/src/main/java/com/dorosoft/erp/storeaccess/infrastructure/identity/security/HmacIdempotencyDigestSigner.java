package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.port.identity.IdempotencyDigestSigner;
import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityHmacProperties;
import org.springframework.stereotype.Component;

@Component
public class HmacIdempotencyDigestSigner implements IdempotencyDigestSigner {

    private final HmacDigester hmacDigester;
    private final IdentityHmacProperties hmacProperties;

    public HmacIdempotencyDigestSigner(HmacDigester hmacDigester, IdentityHmacProperties hmacProperties) {
        this.hmacDigester = hmacDigester;
        this.hmacProperties = hmacProperties;
    }

    @Override
    public String digestKey(String rawIdempotencyKey) {
        return hmacDigester.digest(hmacProperties.idempotencySecret(), rawIdempotencyKey);
    }

    @Override
    public String digestRequest(String canonicalRequest) {
        return hmacDigester.digest(hmacProperties.idempotencySecret(), canonicalRequest);
    }
}
