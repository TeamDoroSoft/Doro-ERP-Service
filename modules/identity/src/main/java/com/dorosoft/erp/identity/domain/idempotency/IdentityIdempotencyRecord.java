package com.dorosoft.erp.identity.domain.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** 계정 발급·비밀번호 초기화의 24시간 성공 재생 상태. */
public final class IdentityIdempotencyRecord {
    public static final Duration RETENTION = Duration.ofHours(24);

    private final UUID recordId;
    private final IdempotencyOperation operation;
    private final byte[] keyDigest;
    private final byte[] requestHmac;
    private final UUID requestedBy;
    private final UUID targetAccountId;
    private final IdempotencyStatus status;
    private final Integer httpStatus;
    private final byte[] responseCiphertext;
    private final byte[] responseNonce;
    private final String masterKeyVersion;
    private final Instant createdAt;
    private final Instant completedAt;
    private final Instant expiresAt;

    public IdentityIdempotencyRecord(
            UUID recordId,
            IdempotencyOperation operation,
            byte[] keyDigest,
            byte[] requestHmac,
            UUID requestedBy,
            UUID targetAccountId,
            IdempotencyStatus status,
            Integer httpStatus,
            byte[] responseCiphertext,
            byte[] responseNonce,
            String masterKeyVersion,
            Instant createdAt,
            Instant completedAt,
            Instant expiresAt
    ) {
        this.recordId = Objects.requireNonNull(recordId, "recordId");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.keyDigest = requireBytes(keyDigest, 32, "keyDigest");
        this.requestHmac = requireBytes(requestHmac, 32, "requestHmac");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.status = Objects.requireNonNull(status, "status");
        this.masterKeyVersion = requireVersion(masterKeyVersion);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.equals(createdAt.plus(RETENTION))) {
            throw new IllegalArgumentException("idempotency record must expire exactly 24 hours after creation");
        }
        validateResult(status, targetAccountId, httpStatus, responseCiphertext, responseNonce, completedAt);
        this.targetAccountId = targetAccountId;
        this.httpStatus = httpStatus;
        this.responseCiphertext = copy(responseCiphertext);
        this.responseNonce = copy(responseNonce);
        this.completedAt = completedAt;
    }

    public static IdentityIdempotencyRecord processing(
            UUID recordId,
            IdempotencyOperation operation,
            byte[] keyDigest,
            byte[] requestHmac,
            UUID requestedBy,
            String masterKeyVersion,
            Instant createdAt
    ) {
        return new IdentityIdempotencyRecord(
                recordId, operation, keyDigest, requestHmac, requestedBy,
                null, IdempotencyStatus.PROCESSING, null, null, null,
                masterKeyVersion, createdAt, null, createdAt.plus(RETENTION)
        );
    }

    public IdentityIdempotencyRecord succeed(
            UUID targetId,
            int successStatus,
            byte[] ciphertext,
            byte[] nonce,
            Instant completedAt
    ) {
        return new IdentityIdempotencyRecord(
                recordId, operation, keyDigest, requestHmac, requestedBy,
                targetId, IdempotencyStatus.SUCCEEDED, successStatus, ciphertext, nonce,
                masterKeyVersion, createdAt, completedAt, expiresAt
        );
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean hasSameRequestHmac(byte[] candidate) {
        return java.security.MessageDigest.isEqual(requestHmac, candidate);
    }

    public UUID recordId() { return recordId; }
    public IdempotencyOperation operation() { return operation; }
    public byte[] keyDigest() { return copy(keyDigest); }
    public byte[] requestHmac() { return copy(requestHmac); }
    public UUID requestedBy() { return requestedBy; }
    public UUID targetAccountId() { return targetAccountId; }
    public IdempotencyStatus status() { return status; }
    public Integer httpStatus() { return httpStatus; }
    public byte[] responseCiphertext() { return copy(responseCiphertext); }
    public byte[] responseNonce() { return copy(responseNonce); }
    public String masterKeyVersion() { return masterKeyVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }
    public Instant expiresAt() { return expiresAt; }

    private static void validateResult(
            IdempotencyStatus status,
            UUID targetId,
            Integer httpStatus,
            byte[] ciphertext,
            byte[] nonce,
            Instant completedAt
    ) {
        if (status == IdempotencyStatus.PROCESSING) {
            if (targetId != null || httpStatus != null || ciphertext != null || nonce != null || completedAt != null) {
                throw new IllegalArgumentException("processing idempotency state cannot contain a result");
            }
            return;
        }
        if (targetId == null || httpStatus == null || completedAt == null) {
            throw new IllegalArgumentException("succeeded idempotency state requires target, status and completion time");
        }
        if (httpStatus == 201) {
            requireBytes(nonce, 12, "responseNonce");
            if (ciphertext == null || ciphertext.length == 0) {
                throw new IllegalArgumentException("201 replay requires encrypted response bytes");
            }
        } else if (httpStatus == 204) {
            if (ciphertext != null || nonce != null) {
                throw new IllegalArgumentException("204 replay cannot contain response bytes");
            }
        } else {
            throw new IllegalArgumentException("only 201 and 204 successes can be replayed");
        }
    }

    private static String requireVersion(String version) {
        Objects.requireNonNull(version, "masterKeyVersion");
        if (version.isBlank() || version.length() > 50) {
            throw new IllegalArgumentException("masterKeyVersion must contain 1 to 50 characters");
        }
        return version;
    }

    private static byte[] requireBytes(byte[] value, int size, String name) {
        Objects.requireNonNull(value, name);
        if (value.length != size) {
            throw new IllegalArgumentException(name + " must be " + size + " bytes");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
