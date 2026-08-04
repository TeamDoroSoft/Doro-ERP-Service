package com.dorosoft.erp.identity.infrastructure.security;

import com.dorosoft.erp.audit.application.api.PrivacyAccessContext;
import com.dorosoft.erp.identity.application.authentication.IdentityPrivacyAccessContextFactory;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Encrypts the trusted client address before it crosses into Feature 17. */
public final class AesGcmIdentityPrivacyAccessContextFactory
        implements IdentityPrivacyAccessContextFactory {
    private static final int AES_256_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String keyVersion;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom;

    public AesGcmIdentityPrivacyAccessContextFactory(String keyVersion, byte[] key) {
        this(keyVersion, key, new SecureRandom());
    }

    AesGcmIdentityPrivacyAccessContextFactory(
            String keyVersion,
            byte[] key,
            SecureRandom secureRandom
    ) {
        if (keyVersion == null || keyVersion.isBlank() || keyVersion.length() > 50) {
            throw new IllegalArgumentException("Privacy address key version must contain 1 to 50 characters");
        }
        if (key == null || key.length != AES_256_KEY_BYTES) {
            throw new IllegalArgumentException("Privacy address key must contain exactly 32 bytes");
        }
        this.keyVersion = keyVersion;
        this.key = new SecretKeySpec(key.clone(), "AES");
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    @Override
    public PrivacyAccessContext create(
            String tenantId,
            UUID accessorAccountId,
            String accessorRoleCode,
            String requestId,
            String trustedClientAddress,
            Instant accessedAt
    ) {
        requireText(tenantId, "tenantId");
        Objects.requireNonNull(accessorAccountId, "accessorAccountId");
        requireText(accessorRoleCode, "accessorRoleCode");
        requireText(requestId, "requestId");
        Objects.requireNonNull(accessedAt, "accessedAt");

        String canonicalAddress;
        try {
            canonicalAddress = InetAddress.ofLiteral(requireText(
                    trustedClientAddress, "trustedClientAddress")).getHostAddress();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Trusted client address is invalid", exception);
        }

        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] ciphertext = encrypt(
                canonicalAddress.getBytes(StandardCharsets.US_ASCII), nonce,
                associatedData(tenantId, keyVersion));
        byte[] envelope = ByteBuffer.allocate(nonce.length + ciphertext.length)
                .put(nonce)
                .put(ciphertext)
                .array();

        return new PrivacyAccessContext(
                tenantId,
                "ADMIN".equals(accessorRoleCode) ? "ADMIN" : "EMPLOYEE",
                accessorAccountId,
                accessorRoleCode,
                requestId,
                Base64.getEncoder().encodeToString(envelope),
                keyVersion,
                accessedAt);
    }

    private byte[] encrypt(byte[] plaintext, byte[] nonce, byte[] associatedData) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData);
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Privacy address encryption is unavailable", exception);
        }
    }

    private static byte[] associatedData(String tenantId, String version) {
        return ("doro-erp/privacy-client-address/v1\0" + tenantId + "\0" + version)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
