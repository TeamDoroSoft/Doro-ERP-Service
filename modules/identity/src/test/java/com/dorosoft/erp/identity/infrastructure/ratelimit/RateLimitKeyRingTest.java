package com.dorosoft.erp.identity.infrastructure.ratelimit;

import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitKeyRingTest {

    @Test
    void acceptsDistinctActiveAndPreviousKeys() {
        RateLimitKeyRing ring = new RateLimitKeyRing(bytes((byte) 1), bytes((byte) 2));

        assertThat(ring.previous()).isPresent();
        assertThat(ring.active().id()).isNotEqualTo(ring.previous().orElseThrow().id());
    }

    @Test
    void rejectsShortOrEqualKeyMaterial() {
        assertThatThrownBy(() -> new RateLimitKeyRing(new byte[31], null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitKeyRing(bytes((byte) 1), bytes((byte) 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesBase64AtTheBoundary() {
        String encoded = Base64.getEncoder().encodeToString(bytes((byte) 3));

        assertThat(RateLimitKeyRing.fromBase64(encoded, "").previous()).isEmpty();
        assertThatThrownBy(() -> RateLimitKeyRing.fromBase64("not-base64", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid Base64");
    }

    static byte[] bytes(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
