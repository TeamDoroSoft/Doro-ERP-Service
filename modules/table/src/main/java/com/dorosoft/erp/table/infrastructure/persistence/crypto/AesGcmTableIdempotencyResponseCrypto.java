package com.dorosoft.erp.table.infrastructure.persistence.crypto;

import com.dorosoft.erp.table.application.idempotency.TableIdempotencyCryptoIntegrityException;
import com.dorosoft.erp.table.application.port.TableIdempotencyResponseCrypto;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * TABLE-00/TABLE-10: {@code table_idempotency_record.response_body}에 저장되는 멱등 재생 응답을
 * AES-256-GCM 봉투 암호화로 보호하는 구현체.
 *
 * <p>저장 형식은 {@code "v1:" + Base64Url(IV) + ":" + Base64Url(ciphertext||tag)}이다. GCM 모드의
 * {@link Cipher#doFinal}은 128bit 인증 태그를 암호문 뒤에 이어 붙여 반환하므로 별도 Column 없이 하나의
 * 문자열에 IV·암호문·인증 태그를 모두 안전하게 담을 수 있다. {@code "v1:"} Prefix는 향후 저장 형식이
 * 바뀌더라도 이전 형식을 명시적으로 구분하기 위한 것으로, 이 Prefix가 없는 값은 (예: 이 기능 도입 이전에
 * 평문으로 저장되었을 수 있는 값) 절대 평문으로 간주하지 않고 손상된 값으로 취급해 복호화를 거부한다.
 *
 * <p>구성된 Key 원문 Byte는 SHA-256으로 정규화해 AES-256 Key로 사용한다. 최소 32byte 이상의 Key만
 * 허용하며, 그보다 짧은 Key는 생성 시점에 즉시 거부한다.
 */
public final class AesGcmTableIdempotencyResponseCrypto implements TableIdempotencyResponseCrypto {

    private static final String FORMAT_VERSION = "v1";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int MINIMUM_KEY_MATERIAL_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom;

    /**
     * @param keyMaterial 최소 32byte의 원문 Key Byte. 이 생성자는 SHA-256으로 정규화한 뒤 전달된
     *     배열을 즉시 0으로 덮어써 호출자의 Heap에 Key 원문이 불필요하게 남지 않도록 한다 — 호출 이후
     *     이 배열을 재사용하지 않아야 한다.
     */
    public AesGcmTableIdempotencyResponseCrypto(byte[] keyMaterial) {
        this(keyMaterial, new SecureRandom());
    }

    AesGcmTableIdempotencyResponseCrypto(byte[] keyMaterial, SecureRandom secureRandom) {
        Objects.requireNonNull(keyMaterial, "keyMaterial");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        if (keyMaterial.length < MINIMUM_KEY_MATERIAL_BYTES) {
            throw new IllegalArgumentException(
                    "table idempotency encryption key must contain at least "
                            + MINIMUM_KEY_MATERIAL_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(sha256(keyMaterial), "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return FORMAT_VERSION + ':' + encode(iv) + ':' + encode(ciphertextAndTag);
        } catch (GeneralSecurityException exception) {
            // Never surface the plaintext, key, or underlying provider message to callers.
            throw new TableIdempotencyCryptoIntegrityException(
                    "Idempotent response could not be encrypted.");
        }
    }

    @Override
    public String decrypt(String stored) {
        Objects.requireNonNull(stored, "stored");
        String[] parts = stored.split(":", 3);
        if (parts.length != 3 || !FORMAT_VERSION.equals(parts[0])) {
            throw corrupt();
        }
        byte[] iv;
        byte[] ciphertextAndTag;
        try {
            iv = decode(parts[1]);
            ciphertextAndTag = decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw corrupt();
        }
        if (iv.length != IV_BYTES) {
            throw corrupt();
        }
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertextAndTag);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            // Tampered ciphertext/tag, or the wrong key: reject without leaking any detail.
            throw corrupt();
        } catch (GeneralSecurityException exception) {
            throw corrupt();
        }
    }

    private static TableIdempotencyCryptoIntegrityException corrupt() {
        return new TableIdempotencyCryptoIntegrityException(
                "Stored idempotent response could not be verified.");
    }

    private static byte[] sha256(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return digest;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        } finally {
            Arrays.fill(input, (byte) 0);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
