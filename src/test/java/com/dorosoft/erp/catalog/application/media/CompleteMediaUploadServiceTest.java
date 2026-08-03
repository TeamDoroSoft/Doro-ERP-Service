package com.dorosoft.erp.catalog.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.catalog.domain.media.InvalidMediaObjectException;
import com.dorosoft.erp.catalog.domain.media.MediaAlreadyRejectedException;
import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import com.dorosoft.erp.catalog.domain.media.MediaNotFoundException;
import com.dorosoft.erp.catalog.domain.media.MediaPublishConflictException;
import com.dorosoft.erp.catalog.domain.media.MediaStatus;
import com.dorosoft.erp.catalog.domain.media.MediaUploadChangedException;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CompleteMediaUploadService - 업로드 완료 검증과 조건부 Public 승격(ADR-007)")
class CompleteMediaUploadServiceTest {

    private static final UUID CATALOG_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();
    private static final String CHECKSUM = validChecksum("content-a");
    private static final long BYTE_SIZE = 1024;
    private static final MediaContentType CONTENT_TYPE = MediaContentType.WEBP;

    private FakeProductMediaRepository mediaRepository;
    private FakeProductObjectStorage storage;
    private CompleteMediaUploadService service;

    @BeforeEach
    void setUp() {
        mediaRepository = new FakeProductMediaRepository();
        storage = new FakeProductObjectStorage();
        service = new CompleteMediaUploadService(mediaRepository, storage);
    }

    private ProductMedia pendingMedia(Instant createdAt) {
        UUID mediaId = UUID.randomUUID();
        ProductMedia media =
                ProductMedia.stage(
                        mediaId, CATALOG_ID, "staging/" + mediaId, CONTENT_TYPE, BYTE_SIZE, CHECKSUM, CREATED_BY, createdAt);
        mediaRepository.save(media);
        return media;
    }

    private ProductMedia pendingMediaWithStaging() {
        ProductMedia media = pendingMedia(Instant.now());
        storage.seedStagingObject(media.mediaId(), CONTENT_TYPE, BYTE_SIZE, CHECKSUM);
        return media;
    }

    @Test
    @DisplayName("존재하지 않는 mediaId는 MediaNotFoundException")
    void throwsWhenMediaNotFound() {
        assertThatThrownBy(() -> service.complete(UUID.randomUUID())).isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    @DisplayName("이미 READY면 최초 결과를 그대로 반환한다(멱등)")
    void idempotentWhenAlreadyReady() {
        ProductMedia media = pendingMediaWithStaging();
        ProductMedia firstResult = service.complete(media.mediaId());

        ProductMedia secondResult = service.complete(media.mediaId());

        assertThat(secondResult.publishedObjectKey()).isEqualTo(firstResult.publishedObjectKey());
        assertThat(secondResult.objectEtag()).isEqualTo(firstResult.objectEtag());
        assertThat(secondResult.readyAt()).isEqualTo(firstResult.readyAt());
    }

    @Test
    @DisplayName("이미 REJECTED면 MediaAlreadyRejectedException")
    void throwsWhenAlreadyRejected() {
        ProductMedia media = pendingMedia(Instant.now());
        mediaRepository.save(media.reject());

        assertThatThrownBy(() -> service.complete(media.mediaId())).isInstanceOf(MediaAlreadyRejectedException.class);
    }

    @Test
    @DisplayName("생성 후 24시간이 지난 PENDING은 REJECTED로 저장하고 MediaAlreadyRejectedException을 던진다")
    void rejectsExpiredPending() {
        ProductMedia media = pendingMedia(Instant.now().minus(25, ChronoUnit.HOURS));

        assertThatThrownBy(() -> service.complete(media.mediaId())).isInstanceOf(MediaAlreadyRejectedException.class);
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.REJECTED);
    }

    @Test
    @DisplayName("Staging Object가 없으면 REJECTED로 저장하고 InvalidMediaObjectException을 던진다")
    void rejectsWhenStagingObjectMissing() {
        ProductMedia media = pendingMedia(Instant.now());

        assertThatThrownBy(() -> service.complete(media.mediaId())).isInstanceOf(InvalidMediaObjectException.class);
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.REJECTED);
    }

    @Test
    @DisplayName("실제 Content-Type이 선언값과 다르면 REJECTED로 저장하고 InvalidMediaObjectException을 던진다")
    void rejectsWhenContentTypeMismatch() {
        ProductMedia media = pendingMedia(Instant.now());
        storage.seedStagingObject(media.mediaId(), MediaContentType.PNG, BYTE_SIZE, CHECKSUM);

        assertThatThrownBy(() -> service.complete(media.mediaId())).isInstanceOf(InvalidMediaObjectException.class);
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.REJECTED);
    }

    @Test
    @DisplayName("실제 크기가 선언값과 다르면 REJECTED로 저장하고 InvalidMediaObjectException을 던진다")
    void rejectsWhenSizeMismatch() {
        ProductMedia media = pendingMedia(Instant.now());
        storage.seedStagingObject(media.mediaId(), CONTENT_TYPE, BYTE_SIZE + 1, CHECKSUM);

        assertThatThrownBy(() -> service.complete(media.mediaId())).isInstanceOf(InvalidMediaObjectException.class);
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.REJECTED);
    }

    @Test
    @DisplayName("실제 Checksum이 선언값과 다르면 REJECTED로 저장하고 InvalidMediaObjectException을 던진다")
    void rejectsWhenChecksumMismatch() {
        ProductMedia media = pendingMedia(Instant.now());
        storage.seedStagingObject(media.mediaId(), CONTENT_TYPE, BYTE_SIZE, validChecksum("different-content"));

        assertThatThrownBy(() -> service.complete(media.mediaId())).isInstanceOf(InvalidMediaObjectException.class);
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.REJECTED);
    }

    @Test
    @DisplayName("검증을 통과하고 Public Key가 비어 있으면 READY로 승격하고 Staging을 삭제한다")
    void promotesToReadyOnSuccess() {
        ProductMedia media = pendingMediaWithStaging();

        ProductMedia result = service.complete(media.mediaId());

        assertThat(result.status()).isEqualTo(MediaStatus.READY);
        assertThat(result.publishedObjectKey()).isNotBlank();
        assertThat(result.objectEtag()).isNotBlank();
        assertThat(result.readyAt()).isNotNull();
        assertThat(storage.isStagingDeleted(media.mediaId())).isTrue();
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.READY);
    }

    @Test
    @DisplayName("HEAD 검증 이후 Staging이 바뀌면 REJECTED로 저장하고 MediaUploadChangedException을 던진다")
    void rejectsWhenSourceChangedAfterVerification() {
        ProductMedia media = pendingMediaWithStaging();
        storage.simulateConcurrentOverwriteOnNextPromote(media.mediaId());

        assertThatThrownBy(() -> service.complete(media.mediaId())).isInstanceOf(MediaUploadChangedException.class);
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.REJECTED);
    }

    @Test
    @DisplayName("Public Key가 이미 같은 Metadata로 존재하면 이전 성공을 복구해 READY로 저장한다")
    void recoversWhenExistingPublicObjectMatches() {
        ProductMedia media = pendingMediaWithStaging();
        storage.seedMatchingPublicObject(media.mediaId(), CHECKSUM, CONTENT_TYPE, BYTE_SIZE);

        ProductMedia result = service.complete(media.mediaId());

        assertThat(result.status()).isEqualTo(MediaStatus.READY);
        assertThat(storage.isStagingDeleted(media.mediaId())).isTrue();
    }

    @Test
    @DisplayName("Public Key가 다른 Metadata로 이미 존재하면 REJECTED로 저장하고 MediaPublishConflictException을 던진다")
    void rejectsWhenExistingPublicObjectMismatches() {
        ProductMedia media = pendingMediaWithStaging();
        storage.seedConflictingPublicObject(media.mediaId(), CHECKSUM, CONTENT_TYPE, BYTE_SIZE + 999);

        assertThatThrownBy(() -> service.complete(media.mediaId())).isInstanceOf(MediaPublishConflictException.class);
        assertThat(mediaRepository.findById(media.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.REJECTED);
    }

    private static String validChecksum(String seed) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(seed.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
