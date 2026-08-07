package com.dorosoft.erp.commerce.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Actor Context 위조 방지 서명. Edge·Store Access와 Commerce가 공유하는 서비스 자격증명이다.
 */
final class ActorContextSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private ActorContextSignature() {
    }

    static String canonical(
            String tenantId, String storeId, String actorType, String actorId, String role, String issuedAt) {
        return String.join("\n",
                tenantId == null ? "" : tenantId,
                storeId == null ? "" : storeId,
                actorType == null ? "" : actorType,
                actorId == null ? "" : actorId,
                role == null ? "" : role,
                issuedAt == null ? "" : issuedAt);
    }

    static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("actor context signature could not be computed", exception);
        }
    }

    /** 시간 비교 부채널을 피하기 위해 상수 시간 비교를 사용한다. */
    static boolean matches(String expected, String candidate) {
        if (expected == null || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }
}
