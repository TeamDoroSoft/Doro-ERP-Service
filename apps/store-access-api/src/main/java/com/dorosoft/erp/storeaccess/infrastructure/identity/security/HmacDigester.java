package com.dorosoft.erp.storeaccess.infrastructure.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Computes HMAC-SHA-256 digests over a caller-supplied purpose secret (ADR-02-007, ADR-02-013, ADR-02-014).
 * Callers select which purpose-scoped secret to pass; this component never stores or logs raw input or
 * secrets.
 */
@Component
public class HmacDigester {

    private static final String ALGORITHM = "HmacSHA256";

    public String digest(String secret, String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to compute HMAC digest", e);
        }
    }
}
