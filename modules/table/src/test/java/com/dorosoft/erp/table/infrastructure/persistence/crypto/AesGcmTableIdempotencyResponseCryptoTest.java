package com.dorosoft.erp.table.infrastructure.persistence.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dorosoft.erp.table.application.idempotency.TableIdempotencyCryptoIntegrityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TABLE-10 인수·보안 검증: {@code table_idempotency_record.response_body}에 저장되는 값을 만드는
 * AES-256-GCM 봉투 암호화 구현 자체에 대한 순수 단위 테스트. Spring Context, DB, Docker가 필요 없다.
 */
class AesGcmTableIdempotencyResponseCryptoTest {

    private static final String SECRET_MARKER = "TOP-SECRET-QR-ACCESS-URL-abc123";

    @Test
    @DisplayName("암호화 후 복호화하면 원본 평문과 동일한 값을 반환한다")
    void encryptThenDecrypt_roundTripsToOriginalPlaintext() {
        AesGcmTableIdempotencyResponseCrypto crypto = newCrypto(key((byte) 1));
        String plaintext = "{\"credentialId\":\"c-1\"," + "\"accessUrl\":\"" + SECRET_MARKER + "\"}";

        String stored = crypto.encrypt(plaintext);
        String decrypted = crypto.decrypt(stored);

        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("저장 값에는 원본 평문(JSON)이 그대로 포함되지 않는다")
    void encrypt_neverContainsThePlaintext() {
        AesGcmTableIdempotencyResponseCrypto crypto = newCrypto(key((byte) 2));
        String plaintext = "{\"accessUrl\":\"" + SECRET_MARKER + "\"}";

        String stored = crypto.encrypt(plaintext);

        assertFalse(stored.contains(SECRET_MARKER), "stored envelope must not contain the plaintext secret");
        assertFalse(stored.contains("accessUrl"), "stored envelope must not contain plaintext JSON field names");
    }

    @Test
    @DisplayName("같은 평문을 두 번 암호화하면 IV가 달라 저장 값도 서로 다르다")
    void encrypt_usesFreshIvEachTime() {
        AesGcmTableIdempotencyResponseCrypto crypto = newCrypto(key((byte) 3));
        String plaintext = "{\"status\":\"ACTIVE\"}";

        String first = crypto.encrypt(plaintext);
        String second = crypto.encrypt(plaintext);

        assertNotEquals(first, second, "each encryption must use a fresh random IV/nonce");
        String firstIv = first.split(":", 3)[1];
        String secondIv = second.split(":", 3)[1];
        assertNotEquals(firstIv, secondIv);
        // But both must still decrypt back to the same original plaintext.
        assertEquals(plaintext, crypto.decrypt(first));
        assertEquals(plaintext, crypto.decrypt(second));
    }

    @Test
    @DisplayName("저장 형식은 v1:IV:암호문 세 부분으로 구성된다")
    void encrypt_producesTheDocumentedStorageFormat() {
        AesGcmTableIdempotencyResponseCrypto crypto = newCrypto(key((byte) 4));

        String stored = crypto.encrypt("{}");

        String[] parts = stored.split(":", 3);
        assertEquals(3, parts.length);
        assertEquals("v1", parts[0]);
        assertEquals(12, Base64.getUrlDecoder().decode(parts[1]).length, "IV must be 12 bytes");
        // ciphertext must include the 16-byte GCM authentication tag appended.
        assertTrue(Base64.getUrlDecoder().decode(parts[2]).length >= 16);
    }

    @Test
    @DisplayName("암호문 본문이 변조되면 복호화가 거부된다")
    void decrypt_rejectsTamperedCiphertext() {
        AesGcmTableIdempotencyResponseCrypto crypto = newCrypto(key((byte) 5));
        // A 19-byte plaintext produces a 19-byte ciphertext body followed by a 16-byte GCM tag;
        // flipping the very first byte lands inside the ciphertext body, not the tag.
        String stored = crypto.encrypt("{\"status\":\"ACTIVE\"}");
        String tampered = flipByteOfSegmentFromStart(stored, 2, 0);

        assertThrows(TableIdempotencyCryptoIntegrityException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    @DisplayName("인증 태그(암호문 마지막 16byte)가 변조되면 복호화가 거부된다")
    void decrypt_rejectsTamperedAuthenticationTag() {
        AesGcmTableIdempotencyResponseCrypto crypto = newCrypto(key((byte) 6));
        String stored = crypto.encrypt("{\"status\":\"ACTIVE\"}");
        // Flip the very last byte of the ciphertext segment, which falls inside the
        // 16-byte GCM tag appended by Cipher#doFinal.
        String tampered = flipByteOfSegmentFromEnd(stored, 2, 1);

        assertThrows(TableIdempotencyCryptoIntegrityException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    @DisplayName("잘못된 Key로 복호화하면 거부된다")
    void decrypt_rejectsWhenKeyDoesNotMatch() {
        AesGcmTableIdempotencyResponseCrypto encryptor = newCrypto(key((byte) 7));
        AesGcmTableIdempotencyResponseCrypto otherDecryptor = newCrypto(key((byte) 8));
        String stored = encryptor.encrypt("{\"status\":\"ACTIVE\"}");

        assertThrows(TableIdempotencyCryptoIntegrityException.class, () -> otherDecryptor.decrypt(stored));
    }

    @Test
    @DisplayName("형식이 손상된 저장 값(Prefix 없음, 구간 수 불일치, 잘못된 Base64)은 평문으로 오인되지 않고 거부된다")
    void decrypt_rejectsMalformedStoredValues() {
        AesGcmTableIdempotencyResponseCrypto crypto = newCrypto(key((byte) 9));

        assertThrows(TableIdempotencyCryptoIntegrityException.class,
                () -> crypto.decrypt("{\"status\":\"ACTIVE\"}")); // legacy-looking plaintext must not be accepted
        assertThrows(TableIdempotencyCryptoIntegrityException.class,
                () -> crypto.decrypt("v2:abc:def")); // unknown format version
        assertThrows(TableIdempotencyCryptoIntegrityException.class,
                () -> crypto.decrypt("v1:onlyonepart"));
        assertThrows(TableIdempotencyCryptoIntegrityException.class,
                () -> crypto.decrypt("v1:not-valid-base64-!!!:not-valid-base64-!!!"));
    }

    @Test
    @DisplayName("IV가 12byte가 아니면 거부된다")
    void decrypt_rejectsWrongLengthIv() {
        AesGcmTableIdempotencyResponseCrypto crypto = newCrypto(key((byte) 10));
        String wrongIv = "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[8]) + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

        assertThrows(TableIdempotencyCryptoIntegrityException.class, () -> crypto.decrypt(wrongIv));
    }

    @Test
    @DisplayName("32byte 미만의 Key로는 생성 자체가 거부된다")
    void constructor_rejectsKeysShorterThanThirtyTwoBytes() {
        byte[] shortKey = new byte[31];
        new SecureRandom().nextBytes(shortKey);

        assertThrows(IllegalArgumentException.class, () -> new AesGcmTableIdempotencyResponseCrypto(shortKey));
    }

    private static AesGcmTableIdempotencyResponseCrypto newCrypto(byte[] keyMaterial) {
        return new AesGcmTableIdempotencyResponseCrypto(keyMaterial);
    }

    private static byte[] key(byte fill) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, fill);
        return bytes;
    }

    /** Flips one bit inside the {@code fromEnd}-th byte from the end of the given colon-separated segment. */
    private static String flipByteOfSegmentFromEnd(String stored, int segmentIndex, int fromEnd) {
        String[] parts = stored.split(":", 3);
        byte[] decoded = Base64.getUrlDecoder().decode(parts[segmentIndex]);
        decoded[decoded.length - fromEnd] ^= 0x01;
        parts[segmentIndex] = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
        return String.join(":", parts);
    }

    /** Flips one bit inside the {@code fromStart}-th byte from the start of the given colon-separated segment. */
    private static String flipByteOfSegmentFromStart(String stored, int segmentIndex, int fromStart) {
        String[] parts = stored.split(":", 3);
        byte[] decoded = Base64.getUrlDecoder().decode(parts[segmentIndex]);
        decoded[fromStart] ^= 0x01;
        parts[segmentIndex] = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
        return String.join(":", parts);
    }
}
