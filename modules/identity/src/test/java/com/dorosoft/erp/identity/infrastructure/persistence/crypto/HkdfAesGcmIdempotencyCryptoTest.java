package com.dorosoft.erp.identity.infrastructure.persistence.crypto;

import com.dorosoft.erp.identity.application.port.IdempotencyKeyRing;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HkdfAesGcmIdempotencyCryptoTest {
    private final IdempotencyKeyRing keyRing = new IdempotencyKeyRing(
            "active-v2", bytes(1), "previous-v1", bytes(2)
    );
    private final HkdfAesGcmIdempotencyCrypto crypto = new HkdfAesGcmIdempotencyCrypto(keyRing);

    @Test
    void activeAndPreviousDigestCandidatesAreAvailableDuringRotation() {
        var candidates = crypto.keyDigestCandidates(IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, "key-01");
        assertArrayEquals(candidates.get(0).keyDigest(), crypto.keyDigestCandidates(
                IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, "key-01"
        ).get(0).keyDigest());
        assertFalse(Arrays.equals(candidates.get(0).keyDigest(), candidates.get(1).keyDigest()));
    }

    @Test
    void operationAndPurposeDerivationsAreSeparated() {
        byte[] canonical = "canonical-request".getBytes(StandardCharsets.UTF_8);
        byte[] createHmac = crypto.requestHmac(
                IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, canonical, "active-v2"
        );
        byte[] resetHmac = crypto.requestHmac(
                IdempotencyOperation.EMPLOYEE_PASSWORD_RESET, canonical, "active-v2"
        );
        byte[] digest = crypto.keyDigestCandidates(
                IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, "canonical-request"
        ).getFirst().keyDigest();
        assertFalse(Arrays.equals(createHmac, resetHmac));
        assertFalse(Arrays.equals(createHmac, digest));
    }

    @Test
    void aes256GcmRoundTripBindsOperationAndMasterKeyVersionAsAad() {
        byte[] projection = "{\"accountId\":\"safe\"}".getBytes(StandardCharsets.UTF_8);
        var encrypted = crypto.encryptResponse(IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, projection);
        assertArrayEquals(projection, crypto.decryptResponse(
                IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE,
                encrypted.masterKeyVersion(), encrypted.nonce(), encrypted.ciphertext()
        ));
        assertThrows(IllegalStateException.class, () -> crypto.decryptResponse(
                IdempotencyOperation.EMPLOYEE_PASSWORD_RESET,
                encrypted.masterKeyVersion(), encrypted.nonce(), encrypted.ciphertext()
        ));
    }

    @Test
    void encryptionUsesAUniqueNinetySixBitNonce() {
        byte[] payload = "projection".getBytes(StandardCharsets.UTF_8);
        var first = crypto.encryptResponse(IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, payload);
        var second = crypto.encryptResponse(IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, payload);
        assertFalse(Arrays.equals(first.nonce(), second.nonce()));
        assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
    }

    @Test
    void keyRingRejectsIncompleteOrDuplicateRotationState() {
        assertThrows(IllegalArgumentException.class, () ->
                new IdempotencyKeyRing("v1", bytes(1), "v0", null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new IdempotencyKeyRing("v1", bytes(1), "v1", bytes(2))
        );
        assertThrows(IllegalArgumentException.class, () ->
                new IdempotencyKeyRing("v1", bytes(1), "v0", bytes(1))
        );
    }

    private static byte[] bytes(int seed) {
        byte[] value = new byte[32];
        Arrays.fill(value, (byte) seed);
        return value;
    }
}
