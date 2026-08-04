package com.dorosoft.erp.catalog.application.media;

import com.dorosoft.erp.catalog.application.port.PresignedUpload;
import com.dorosoft.erp.catalog.application.port.ProductObjectStorage;
import com.dorosoft.erp.catalog.application.port.PromotionOutcome;
import com.dorosoft.erp.catalog.application.port.S3ObjectMetadata;
import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * TBD 종결안이 명시한 "단위·Application 테스트는 Fake·Mock Media Adapter"를 구현한 Test Double이다.
 * S3ProductObjectStorageAdapter와 같은 규칙(Target 존재 우선 확인 -> Source ETag 조건부 복사)을 흉내 낸다.
 */
class FakeProductObjectStorage implements ProductObjectStorage {

    private record StagedObject(String etag, MediaContentType contentType, long byteSize, String checksumBase64) {}

    private final Map<UUID, StagedObject> stagingObjects = new HashMap<>();
    private final Map<String, S3ObjectMetadata> publicObjects = new HashMap<>();
    private final Set<UUID> deletedStagingMediaIds = new HashSet<>();
    private final Set<UUID> raceOnNextPromote = new HashSet<>();
    private int etagSequence = 0;

    @Override
    public PresignedUpload createStagingUpload(UUID mediaId, MediaContentType contentType, String checksumSha256Base64) {
        return new PresignedUpload(
                "staging/" + mediaId,
                "https://fake-upload.test/" + mediaId,
                Map.of("Content-Type", contentType.mimeType(), "x-amz-checksum-sha256", checksumSha256Base64),
                Instant.now().plus(ProductMedia.PRESIGNED_UPLOAD_TTL));
    }

    @Override
    public Optional<S3ObjectMetadata> inspectStagingObject(UUID mediaId) {
        StagedObject staged = stagingObjects.get(mediaId);
        if (staged == null) {
            return Optional.empty();
        }
        return Optional.of(new S3ObjectMetadata(staged.etag(), staged.contentType().mimeType(), staged.byteSize(), staged.checksumBase64()));
    }

    @Override
    public PromotionOutcome promotePublic(
            UUID mediaId, String expectedEtag, String verifiedChecksumSha256Base64, MediaContentType contentType) {
        String publicKey = publicKeyFor(mediaId, verifiedChecksumSha256Base64, contentType);

        S3ObjectMetadata existing = publicObjects.get(publicKey);
        if (existing != null) {
            return PromotionOutcome.targetAlreadyExists(publicKey, existing);
        }

        if (raceOnNextPromote.remove(mediaId)) {
            changeStagingEtag(mediaId);
        }

        StagedObject currentStaging = stagingObjects.get(mediaId);
        if (currentStaging == null || !currentStaging.etag().equals(expectedEtag)) {
            return PromotionOutcome.sourceChanged();
        }

        String newEtag = "public-etag-" + (++etagSequence);
        S3ObjectMetadata promoted =
                new S3ObjectMetadata(newEtag, contentType.mimeType(), currentStaging.byteSize(), verifiedChecksumSha256Base64);
        publicObjects.put(publicKey, promoted);
        return PromotionOutcome.promoted(publicKey, newEtag);
    }

    @Override
    public void deleteStagingObjectBestEffort(UUID mediaId) {
        stagingObjects.remove(mediaId);
        deletedStagingMediaIds.add(mediaId);
    }

    // --- 테스트 설정 도우미 -----------------------------------------------------

    void seedStagingObject(UUID mediaId, MediaContentType contentType, long byteSize, String checksumBase64) {
        stagingObjects.put(mediaId, new StagedObject("etag-" + (++etagSequence), contentType, byteSize, checksumBase64));
    }

    void removeStagingObject(UUID mediaId) {
        stagingObjects.remove(mediaId);
    }

    /** 다음 promotePublic 호출 직전에 Staging Object가 다른 요청으로 덮어써진 상황을 흉내 낸다. */
    void simulateConcurrentOverwriteOnNextPromote(UUID mediaId) {
        raceOnNextPromote.add(mediaId);
    }

    private void changeStagingEtag(UUID mediaId) {
        StagedObject existing = stagingObjects.get(mediaId);
        if (existing != null) {
            stagingObjects.put(
                    mediaId,
                    new StagedObject("etag-" + (++etagSequence), existing.contentType(), existing.byteSize(), existing.checksumBase64()));
        }
    }

    /** publicKeyFor와 같은 Key에 실제와 다른 Metadata를 미리 심어 MEDIA_PUBLISH_CONFLICT 경로를 검증한다. */
    void seedConflictingPublicObject(
            UUID mediaId, String checksumUsedForKey, MediaContentType contentType, long differentByteSize) {
        String publicKey = publicKeyFor(mediaId, checksumUsedForKey, contentType);
        publicObjects.put(
                publicKey, new S3ObjectMetadata("existing-etag", contentType.mimeType(), differentByteSize, checksumUsedForKey));
    }

    /** publicKeyFor와 같은 Key에 실제와 동일한 Metadata를 미리 심어 이전 성공 복구 경로를 검증한다. */
    void seedMatchingPublicObject(UUID mediaId, String checksumUsedForKey, MediaContentType contentType, long byteSize) {
        String publicKey = publicKeyFor(mediaId, checksumUsedForKey, contentType);
        publicObjects.put(
                publicKey, new S3ObjectMetadata("existing-etag", contentType.mimeType(), byteSize, checksumUsedForKey));
    }

    boolean isStagingDeleted(UUID mediaId) {
        return deletedStagingMediaIds.contains(mediaId);
    }

    private static String publicKeyFor(UUID mediaId, String checksumBase64, MediaContentType contentType) {
        return "public/" + mediaId + "/" + checksumBase64 + "." + contentType.extension();
    }
}
