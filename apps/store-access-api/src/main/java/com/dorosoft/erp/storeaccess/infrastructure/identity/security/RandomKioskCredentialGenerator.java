package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import com.dorosoft.erp.storeaccess.application.api.identity.KioskCredentialFormat;
import com.dorosoft.erp.storeaccess.application.port.identity.GeneratedKioskCredential;
import com.dorosoft.erp.storeaccess.application.port.identity.KioskCredentialGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates Kiosk device credentials (ADR-02-013): a random lookup {@code credentialId} and a
 * {@code SecureRandom} 32-byte (256-bit) {@code secret}, both Base64URL-encoded without padding.
 */
@Component
public class RandomKioskCredentialGenerator implements KioskCredentialGenerator {

    private static final int CREDENTIAL_ID_BYTES = 24;
    private static final int SECRET_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public GeneratedKioskCredential generate() {
        String credentialId = randomBase64Url(CREDENTIAL_ID_BYTES);
        String secret = randomBase64Url(SECRET_BYTES);
        String fullCredential = KioskCredentialFormat.format(credentialId, secret);
        return new GeneratedKioskCredential(credentialId, secret, fullCredential);
    }

    private String randomBase64Url(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
