package com.dorosoft.erp.catalog.infrastructure.media;

import com.dorosoft.erp.catalog.application.port.PresignedUpload;
import com.dorosoft.erp.catalog.application.port.ProductObjectStorage;
import com.dorosoft.erp.catalog.application.port.PromotionOutcome;
import com.dorosoft.erp.catalog.application.port.S3ObjectMetadata;
import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * ADR-007의 S3 경계 구현. Object Key 조합, Presigned URL 발급, Staging 검증(실제 Byte로 Checksum
 * 재계산)과 조건부 Public 승격을 전담한다. Public Key는 이미 존재하면 승격을 시도하지 않고 기존 Metadata를
 * 반환하며, 없으면 {@code x-amz-copy-source-if-match}로 Source ETag를 조건으로 복사한다.
 */
@Component
class S3ProductObjectStorageAdapter implements ProductObjectStorage {

    private static final int PRECONDITION_FAILED = 412;

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final ProductMediaProperties properties;

    S3ProductObjectStorageAdapter(S3Client s3Client, S3Presigner presigner, ProductMediaProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUpload createStagingUpload(
            UUID mediaId, MediaContentType contentType, String checksumSha256Base64) {
        String stagingKey = ProductMediaKeys.stagingKey(properties.tenantId(), mediaId);

        PutObjectRequest putRequest =
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(stagingKey)
                        .contentType(contentType.mimeType())
                        .checksumSHA256(checksumSha256Base64)
                        .build();
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(ProductMedia.PRESIGNED_UPLOAD_TTL)
                        .putObjectRequest(putRequest)
                        .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        Map<String, String> requiredHeaders = new LinkedHashMap<>();
        requiredHeaders.put("Content-Type", contentType.mimeType());
        requiredHeaders.put("x-amz-checksum-sha256", checksumSha256Base64);

        return new PresignedUpload(
                stagingKey,
                presigned.url().toString(),
                requiredHeaders,
                Instant.now().plus(ProductMedia.PRESIGNED_UPLOAD_TTL));
    }

    @Override
    public Optional<S3ObjectMetadata> inspectStagingObject(UUID mediaId) {
        String stagingKey = ProductMediaKeys.stagingKey(properties.tenantId(), mediaId);
        return inspectObject(stagingKey);
    }

    @Override
    public PromotionOutcome promotePublic(
            UUID mediaId, String expectedEtag, String verifiedChecksumSha256Base64, MediaContentType contentType) {
        String stagingKey = ProductMediaKeys.stagingKey(properties.tenantId(), mediaId);
        String publicKey =
                ProductMediaKeys.publicKey(properties.tenantId(), mediaId, verifiedChecksumSha256Base64, contentType);

        Optional<S3ObjectMetadata> existingTarget = inspectObject(publicKey);
        if (existingTarget.isPresent()) {
            return PromotionOutcome.targetAlreadyExists(publicKey, existingTarget.get());
        }

        try {
            CopyObjectRequest copyRequest =
                    CopyObjectRequest.builder()
                            .sourceBucket(properties.bucket())
                            .sourceKey(stagingKey)
                            .destinationBucket(properties.bucket())
                            .destinationKey(publicKey)
                            .copySourceIfMatch(expectedEtag)
                            // 대상 부재 조건(S3 조건부 쓰기)을 함께 요청한다. 미지원 Backend에서도
                            // publicKey는 sha256 기반 불변 Key이므로 동시 승격은 항상 동일 Byte를 쓴다.
                            .overrideConfiguration(override -> override.putHeader("If-None-Match", "*"))
                            .build();
            CopyObjectResponse response = s3Client.copyObject(copyRequest);
            return PromotionOutcome.promoted(publicKey, stripQuotes(response.copyObjectResult().eTag()));
        } catch (S3Exception e) {
            if (e.statusCode() == PRECONDITION_FAILED) {
                Optional<S3ObjectMetadata> raceTarget = inspectObject(publicKey);
                if (raceTarget.isPresent()) {
                    return PromotionOutcome.targetAlreadyExists(publicKey, raceTarget.get());
                }
                return PromotionOutcome.sourceChanged();
            }
            throw e;
        }
    }

    @Override
    public void deleteStagingObjectBestEffort(UUID mediaId) {
        String stagingKey = ProductMediaKeys.stagingKey(properties.tenantId(), mediaId);
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(properties.bucket()).key(stagingKey).build());
        } catch (RuntimeException e) {
            // 삭제 실패는 완료 결과를 되돌리지 않고 일일 정리 작업 재시도 대상으로 남긴다(ADR-007).
        }
    }

    /**
     * HEAD 대신 실제 Byte를 내려받아 SHA-256을 다시 계산한다. Backend의 Checksum Metadata 지원 여부와
     * 무관하게 "실제 값"을 검증하려는 ADR-007 의도를 그대로 満족한다. 이미지는 5MB 이하로 제한되어 있어 비용이 작다.
     */
    private Optional<S3ObjectMetadata> inspectObject(String key) {
        GetObjectRequest request =
                GetObjectRequest.builder().bucket(properties.bucket()).key(key).build();
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long byteSize = 0;
            try (DigestInputStream digestStream = new DigestInputStream(response, digest)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = digestStream.read(buffer)) != -1) {
                    byteSize += read;
                }
            }
            GetObjectResponse metadata = response.response();
            String checksumBase64 = Base64.getEncoder().encodeToString(digest.digest());
            return Optional.of(
                    new S3ObjectMetadata(
                            stripQuotes(metadata.eTag()), metadata.contentType(), byteSize, checksumBase64));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest를 사용할 수 없습니다", e);
        }
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return null;
        }
        return etag.replace("\"", "");
    }
}
