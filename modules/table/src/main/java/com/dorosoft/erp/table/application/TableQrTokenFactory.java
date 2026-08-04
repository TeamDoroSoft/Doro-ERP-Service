package com.dorosoft.erp.table.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class TableQrTokenFactory {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    GeneratedQrToken generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        byte[] digest = digest(tokenBytes);
        Arrays.fill(tokenBytes, (byte) 0);
        return new GeneratedQrToken(token, digest);
    }

    private static byte[] digest(byte[] tokenBytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(tokenBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    record GeneratedQrToken(String token, byte[] digest) {}
}
