package com.dorosoft.erp.catalog.application.port;

import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import java.util.Optional;
import java.util.UUID;

/**
 * ADR-007의 S3 경계. Presigned URL 발급, Staging 검증과 조건부 Public 승격만 노출한다.
 * Object Key 조합은 Adapter 책임이므로 Application은 mediaId만 전달하고 Key 형식·Bucket명·
 * AWS SDK 타입을 Domain·Application에 노출하지 않는다.
 */
public interface ProductObjectStorage {

    PresignedUpload createStagingUpload(UUID mediaId, MediaContentType contentType, String checksumSha256Base64);

    /** Staging Object가 없으면 빈 값을 반환한다. 실제 Byte를 읽어 Checksum을 재계산해 신뢰한다. */
    Optional<S3ObjectMetadata> inspectStagingObject(UUID mediaId);

    /**
     * Public Key가 이미 있으면 승격을 시도하지 않고 기존 Metadata를 그대로 반환한다.
     * 없으면 Source ETag 일치를 조건(x-amz-copy-source-if-match)으로 복사하고,
     * 승격 직후 다른 요청이 먼저 Public Key를 만들었다면 그 Metadata를 반환한다.
     */
    PromotionOutcome promotePublic(
            UUID mediaId, String expectedEtag, String verifiedChecksumSha256Base64, MediaContentType contentType);

    /** 삭제 실패는 예외를 던지지 않고 정리 작업 재시도 대상으로 남긴다. */
    void deleteStagingObjectBestEffort(UUID mediaId);
}
