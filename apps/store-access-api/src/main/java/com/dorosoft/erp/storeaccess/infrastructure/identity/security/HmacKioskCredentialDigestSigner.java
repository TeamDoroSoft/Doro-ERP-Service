package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.port.identity.KioskCredentialDigestSigner;
import com.dorosoft.erp.storeaccess.infrastructure.identity.config.IdentityHmacProperties;
import org.springframework.stereotype.Component;

@Component
public class HmacKioskCredentialDigestSigner implements KioskCredentialDigestSigner {

    private final HmacDigester hmacDigester;
    private final IdentityHmacProperties hmacProperties;

    public HmacKioskCredentialDigestSigner(HmacDigester hmacDigester, IdentityHmacProperties hmacProperties) {
        this.hmacDigester = hmacDigester;
        this.hmacProperties = hmacProperties;
    }

    @Override
    public String digestSecret(String rawSecret) {
        return hmacDigester.digest(hmacProperties.kioskCredentialSecret(), rawSecret);
    }
}
