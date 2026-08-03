package com.dorosoft.erp.catalog.domain.media;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 상품 이미지 Media Metadata(ADR-007). Binary는 S3에 두고 여기는 상태 전이만 소유한다.
 * PENDING에서만 READY 또는 REJECTED로 전이할 수 있고 그 외 전이는 거부한다.
 */
public record ProductMedia(
        UUID mediaId,
        UUID catalogId,
        String stagingObjectKey,
        String publishedObjectKey,
        String objectEtag,
        String checksumSha256,
        MediaStatus status,
        MediaContentType contentType,
        long byteSize,
        UUID createdBy,
        Instant uploadExpiresAt,
        Instant createdAt,
        Instant readyAt) {

    /** 서버가 생성한 Staging Presigned PUT URL의 유효 기간(ADR-007). */
    public static final Duration PRESIGNED_UPLOAD_TTL = Duration.ofMinutes(5);

    /** 생성 후 이 기간 안에 완료되지 않은 PENDING은 REJECTED 대상이다. */
    public static final Duration PENDING_EXPIRY = Duration.ofHours(24);

    /** 상품 이미지 최대 허용 크기(ADR-007). */
    public static final long MAX_BYTE_SIZE = 5L * 1024 * 1024;

    public ProductMedia {
        Objects.requireNonNull(mediaId, "mediaId는 필수다");
        Objects.requireNonNull(catalogId, "catalogId는 필수다");
        stagingObjectKey = requireText(stagingObjectKey, "stagingObjectKey");
        checksumSha256 = requireText(checksumSha256, "checksumSha256");
        Objects.requireNonNull(status, "status는 필수다");
        Objects.requireNonNull(contentType, "contentType은 필수다");
        Objects.requireNonNull(createdBy, "createdBy는 필수다");
        Objects.requireNonNull(uploadExpiresAt, "uploadExpiresAt은 필수다");
        Objects.requireNonNull(createdAt, "createdAt은 필수다");

        requireValidByteSize(byteSize);
        if (status == MediaStatus.READY
                && (publishedObjectKey == null || objectEtag == null || readyAt == null)) {
            throw new IllegalArgumentException("READY 상태는 publishedObjectKey·objectEtag·readyAt이 모두 필요하다");
        }
    }

    /** 업로드 준비 단계에서 생성하는 최초 PENDING 상태. */
    public static ProductMedia stage(
            UUID mediaId,
            UUID catalogId,
            String stagingObjectKey,
            MediaContentType contentType,
            long byteSize,
            String checksumSha256,
            UUID createdBy,
            Instant createdAt) {
        return new ProductMedia(
                mediaId,
                catalogId,
                stagingObjectKey,
                null,
                null,
                checksumSha256,
                MediaStatus.PENDING,
                contentType,
                byteSize,
                createdBy,
                createdAt.plus(PRESIGNED_UPLOAD_TTL),
                createdAt,
                null);
    }

    /** byteSize가 0 미만이거나 MAX_BYTE_SIZE를 넘으면 거부한다. Media Row를 만들기 전에도 호출할 수 있다. */
    public static void requireValidByteSize(long byteSize) {
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize는 0 이상이어야 한다");
        }
        if (byteSize > MAX_BYTE_SIZE) {
            throw new IllegalArgumentException("byteSize는 " + MAX_BYTE_SIZE + " 이하여야 한다");
        }
    }

    public boolean isPending() {
        return status == MediaStatus.PENDING;
    }

    public boolean isReady() {
        return status == MediaStatus.READY;
    }

    public boolean isRejected() {
        return status == MediaStatus.REJECTED;
    }

    /** 생성 후 24시간이 지난 PENDING인지 여부. PENDING이 아니면 항상 false다. */
    public boolean isPendingExpired(Instant now) {
        return isPending() && !now.isBefore(createdAt.plus(PENDING_EXPIRY));
    }

    /** 완료 검증 실패·만료로 거부한다. PENDING이 아니면 거부한다. */
    public ProductMedia reject() {
        requirePending("REJECTED 전환");
        return new ProductMedia(
                mediaId,
                catalogId,
                stagingObjectKey,
                null,
                null,
                checksumSha256,
                MediaStatus.REJECTED,
                contentType,
                byteSize,
                createdBy,
                uploadExpiresAt,
                createdAt,
                null);
    }

    /** 조건부 Public 승격이 성공(또는 이전 성공을 복구)했을 때 READY로 전환한다. */
    public ProductMedia promoteToReady(String publishedObjectKey, String objectEtag, Instant readyAt) {
        requirePending("READY 전환");
        String publicKey = requireText(publishedObjectKey, "publishedObjectKey");
        String etag = requireText(objectEtag, "objectEtag");
        Objects.requireNonNull(readyAt, "readyAt은 필수다");
        return new ProductMedia(
                mediaId,
                catalogId,
                stagingObjectKey,
                publicKey,
                etag,
                checksumSha256,
                MediaStatus.READY,
                contentType,
                byteSize,
                createdBy,
                uploadExpiresAt,
                createdAt,
                readyAt);
    }

    private void requirePending(String action) {
        if (status != MediaStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 " + action + "이 가능합니다. 현재 상태=" + status);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "은(는) 필수다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 공백일 수 없다");
        }
        return value;
    }
}
