package com.dorosoft.erp.catalog.application.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import com.dorosoft.erp.catalog.domain.media.MediaStatus;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MediaExpirySweepService - 만료 PENDING 정리(ADR-007)")
class MediaExpirySweepServiceTest {

    private static final UUID CATALOG_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();
    private static final String CHECKSUM = validChecksum();

    private FakeProductMediaRepository mediaRepository;
    private FakeProductObjectStorage storage;
    private MediaExpirySweepService service;

    @BeforeEach
    void setUp() {
        mediaRepository = new FakeProductMediaRepository();
        storage = new FakeProductObjectStorage();
        service = new MediaExpirySweepService(mediaRepository, storage);
    }

    private ProductMedia stage(Instant createdAt) {
        UUID mediaId = UUID.randomUUID();
        ProductMedia media =
                ProductMedia.stage(
                        mediaId, CATALOG_ID, "staging/" + mediaId, MediaContentType.WEBP, 1024, CHECKSUM, CREATED_BY, createdAt);
        mediaRepository.save(media);
        storage.seedStagingObject(mediaId, MediaContentType.WEBP, 1024, CHECKSUM);
        return media;
    }

    @Test
    @DisplayName("생성 후 24시간이 지난 PENDING만 REJECTED로 전환하고 Staging을 정리한다")
    void rejectsOnlyExpiredPending() {
        ProductMedia expired = stage(Instant.now().minus(25, ChronoUnit.HOURS));
        ProductMedia fresh = stage(Instant.now().minus(1, ChronoUnit.HOURS));

        int rejectedCount = service.rejectExpiredPending();

        assertThat(rejectedCount).isEqualTo(1);
        assertThat(mediaRepository.findById(expired.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.REJECTED);
        assertThat(mediaRepository.findById(fresh.mediaId()).orElseThrow().status()).isEqualTo(MediaStatus.PENDING);
        assertThat(storage.isStagingDeleted(expired.mediaId())).isTrue();
        assertThat(storage.isStagingDeleted(fresh.mediaId())).isFalse();
    }

    @Test
    @DisplayName("정리 대상이 없으면 0을 반환한다")
    void returnsZeroWhenNothingExpired() {
        stage(Instant.now());

        assertThat(service.rejectExpiredPending()).isZero();
    }

    private static String validChecksum() {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest("sweep".getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
