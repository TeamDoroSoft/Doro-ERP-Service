package com.dorosoft.erp.identity.infrastructure.persistence.crypto;

import com.dorosoft.erp.identity.application.port.IdempotencyCryptoPort;
import com.dorosoft.erp.identity.application.port.IdempotencyKeyRing;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** RFC 5869 HKDF 목적 분리와 AES-256-GCM을 적용하는 멱등 Crypto Adapter. */
public final class HkdfAesGcmIdempotencyCrypto implements IdempotencyCryptoPort {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final byte[] HKDF_SALT =
            "doro-erp.identity.idempotency.hkdf.v1".getBytes(StandardCharsets.UTF_8);
    private static final int DERIVED_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final IdempotencyKeyRing keyRing;
    private final SecureRandom secureRandom;

    public HkdfAesGcmIdempotencyCrypto(IdempotencyKeyRing keyRing) {
        this(keyRing, new SecureRandom());
    }

    HkdfAesGcmIdempotencyCrypto(IdempotencyKeyRing keyRing, SecureRandom secureRandom) {
        this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public List<KeyDigestCandidate> keyDigestCandidates(
            IdempotencyOperation operation,
            String rawIdempotencyKey
    ) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(rawIdempotencyKey, "rawIdempotencyKey");
        if (rawIdempotencyKey.length() < 1 || rawIdempotencyKey.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 200 characters");
        }
        List<KeyDigestCandidate> candidates = new ArrayList<>();
        for (IdempotencyKeyRing.KeyMaterial material : keyRing.readCandidates()) {
            byte[] key = derive(material.key(), operation, "key-digest");
            candidates.add(new KeyDigestCandidate(
                    material.version(),
                    hmac(key, rawIdempotencyKey.getBytes(StandardCharsets.UTF_8))
            ));
            Arrays.fill(key, (byte) 0);
        }
        return List.copyOf(candidates);
    }

    @Override
    public byte[] requestHmac(
            IdempotencyOperation operation,
            byte[] canonicalRequest,
            String masterKeyVersion
    ) {
        Objects.requireNonNull(canonicalRequest, "canonicalRequest");
        IdempotencyKeyRing.KeyMaterial material = keyRing.requireVersion(masterKeyVersion);
        byte[] key = derive(material.key(), operation, "request-hmac");
        try {
            return hmac(key, canonicalRequest);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    @Override
    public EncryptedPayload encryptResponse(IdempotencyOperation operation, byte[] responseProjection) {
        Objects.requireNonNull(responseProjection, "responseProjection");
        IdempotencyKeyRing.KeyMaterial material = keyRing.active();
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] ciphertext = crypt(
                Cipher.ENCRYPT_MODE, operation, material, nonce, responseProjection
        );
        return new EncryptedPayload(material.version(), nonce, ciphertext);
    }

    @Override
    public byte[] decryptResponse(
            IdempotencyOperation operation,
            String masterKeyVersion,
            byte[] nonce,
            byte[] ciphertext
    ) {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(ciphertext, "ciphertext");
        if (nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("AES-GCM nonce must be 12 bytes");
        }
        return crypt(
                Cipher.DECRYPT_MODE,
                operation,
                keyRing.requireVersion(masterKeyVersion),
                nonce,
                ciphertext
        );
    }

    @Override
    public String activeMasterKeyVersion() {
        return keyRing.active().version();
    }

    private byte[] crypt(
            int mode,
            IdempotencyOperation operation,
            IdempotencyKeyRing.KeyMaterial material,
            byte[] nonce,
            byte[] input
    ) {
        byte[] key = derive(material.key(), operation, "response-aes-256-gcm");
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(operation, material.version()));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("idempotency response cryptographic operation failed", exception);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] derive(byte[] masterKey, IdempotencyOperation operation, String purpose) {
        byte[] prk = hmac(HKDF_SALT, masterKey);
        byte[] info = ("doro-erp.identity.idempotency."
                + operation.name() + "." + purpose + ".v1").getBytes(StandardCharsets.UTF_8);
        try {
            return hkdfExpand(prk, info, DERIVED_KEY_BYTES);
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int size) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        byte[] previous = new byte[0];
        for (int counter = 1; output.size() < size; counter++) {
            byte[] frame = new byte[previous.length + info.length + 1];
            System.arraycopy(previous, 0, frame, 0, previous.length);
            System.arraycopy(info, 0, frame, previous.length, info.length);
            frame[frame.length - 1] = (byte) counter;
            previous = hmac(prk, frame);
            output.write(previous, 0, Math.min(previous.length, size - output.size()));
        }
        return output.toByteArray();
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 operation failed", exception);
        }
    }

    private static byte[] aad(IdempotencyOperation operation, String version) {
        return (operation.name() + "\u0000" + version).getBytes(StandardCharsets.UTF_8);
    }
}
