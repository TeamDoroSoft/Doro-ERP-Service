package com.dorosoft.erp.audit.infrastructure;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HmacSha256AuditPayloadSignerTest {
    @Test
    void signsDeterministicallyWithoutExposingKeyMaterial() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        HmacSha256AuditPayloadSigner signer = new HmacSha256AuditPayloadSigner(key);

        byte[] first = signer.sign("canonical".getBytes(StandardCharsets.UTF_8));
        byte[] second = signer.sign("canonical".getBytes(StandardCharsets.UTF_8));

        assertEquals(32, first.length);
        assertArrayEquals(first, second);
    }

    @Test
    void rejectsShortKeys() {
        assertThrows(IllegalArgumentException.class, () -> new HmacSha256AuditPayloadSigner(new byte[31]));
    }

    @Test
    void signsWithActiveAndVerifiesThePreviousKeyDuringRotation() {
        byte[] active = filled((byte) 8);
        byte[] previous = filled((byte) 9);
        byte[] payload = "canonical".getBytes(StandardCharsets.UTF_8);
        byte[] previousSignature = new HmacSha256AuditPayloadSigner(previous).sign(payload);
        HmacSha256AuditPayloadSigner rotating = new HmacSha256AuditPayloadSigner(active, previous);

        assertTrue(rotating.matches(payload, previousSignature));
        assertFalse(Arrays.equals(rotating.sign(payload), previousSignature));
        assertThrows(IllegalArgumentException.class,
                () -> new HmacSha256AuditPayloadSigner(active, active));
    }

    private byte[] filled(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }
}
