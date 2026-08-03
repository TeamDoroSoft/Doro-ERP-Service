package com.dorosoft.erp.catalog.domain.media;

import java.util.Base64;
import java.util.Objects;

/** Base64로 인코딩된 SHA-256 Checksum의 형식 검증. Key 형식(16진수 변환 등)은 Infrastructure Adapter가 담당한다. */
public final class Sha256Checksums {

    private static final int SHA256_BYTE_LENGTH = 32;

    private Sha256Checksums() {}

    /** 유효한 Base64 SHA-256(32byte)이 아니면 IllegalArgumentException을 던진다. */
    public static void requireValidBase64(String checksumSha256Base64) {
        Objects.requireNonNull(checksumSha256Base64, "checksumSha256Base64는 필수다");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(checksumSha256Base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("checksumSha256은 유효한 Base64여야 한다", e);
        }
        if (decoded.length != SHA256_BYTE_LENGTH) {
            throw new IllegalArgumentException(
                    "checksumSha256은 SHA-256(32byte) Base64여야 한다. 실제 byte 수=" + decoded.length);
        }
    }
}
