package com.dorosoft.erp.catalog.domain.media;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sha256Checksums 형식 검증")
class Sha256ChecksumsTest {

    @Test
    @DisplayName("유효한 32byte Base64 SHA-256은 통과한다")
    void acceptsValidChecksum() throws Exception {
        String validBase64 = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest("x".getBytes()));

        assertThatCode(() -> Sha256Checksums.requireValidBase64(validBase64)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Base64 형식이 아니면 IllegalArgumentException")
    void rejectsInvalidBase64() {
        assertThatThrownBy(() -> Sha256Checksums.requireValidBase64("not-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("32byte가 아니면 IllegalArgumentException")
    void rejectsWrongLength() {
        String shortBase64 = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> Sha256Checksums.requireValidBase64(shortBase64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null이면 NullPointerException")
    void rejectsNull() {
        assertThatThrownBy(() -> Sha256Checksums.requireValidBase64(null)).isInstanceOf(NullPointerException.class);
    }
}
