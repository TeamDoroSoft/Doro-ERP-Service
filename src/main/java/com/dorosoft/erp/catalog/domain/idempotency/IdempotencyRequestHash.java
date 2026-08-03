package com.dorosoft.erp.catalog.domain.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * 같은 Idempotency-Key 재요청이 같은 내용인지 비교하기 위한 요청 필드 SHA-256 Hex다.
 * Category·Product·Media 등 Catalog의 모든 생성 API가 공유한다(API 명세 공통 계약).
 */
public final class IdempotencyRequestHash {

    private IdempotencyRequestHash() {}

    public static String of(Object... fields) {
        String canonical = Arrays.stream(fields).map(String::valueOf).collect(Collectors.joining(" "));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest를 사용할 수 없습니다", e);
        }
    }
}
