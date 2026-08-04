package com.dorosoft.erp.catalog.application.media;

import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.application.port.ProductObjectStorage;
import com.dorosoft.erp.catalog.application.port.PromotionOutcome;
import com.dorosoft.erp.catalog.application.port.S3ObjectMetadata;
import com.dorosoft.erp.catalog.domain.media.InvalidMediaObjectException;
import com.dorosoft.erp.catalog.domain.media.MediaAlreadyRejectedException;
import com.dorosoft.erp.catalog.domain.media.MediaNotFoundException;
import com.dorosoft.erp.catalog.domain.media.MediaPublishConflictException;
import com.dorosoft.erp.catalog.domain.media.MediaUploadChangedException;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업로드 완료 검증과 조건부 Public 승격(ADR-007). 상품 이미지 등록 Sequence의 Media 책임을 그대로 옮긴다.
 * 검증·승격 실패는 모두 Media를 REJECTED로 저장한 뒤 예외로 사유를 알린다.
 */
@Service
public class CompleteMediaUploadService {

    private final ProductMediaRepository repository;
    private final ProductObjectStorage storage;

    public CompleteMediaUploadService(ProductMediaRepository repository, ProductObjectStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional
    public ProductMedia complete(UUID mediaId) {
        ProductMedia media = repository.findByIdForUpdate(mediaId).orElseThrow(() -> new MediaNotFoundException(mediaId));

        if (media.isReady()) {
            return media;
        }
        if (media.isRejected()) {
            throw new MediaAlreadyRejectedException(mediaId);
        }

        Instant now = Instant.now();
        if (media.isPendingExpired(now)) {
            repository.save(media.reject());
            throw new MediaAlreadyRejectedException(mediaId);
        }

        Optional<S3ObjectMetadata> stagingMetadata = storage.inspectStagingObject(mediaId);
        if (stagingMetadata.isEmpty()) {
            repository.save(media.reject());
            throw new InvalidMediaObjectException(mediaId, "Staging Object가 존재하지 않습니다");
        }

        S3ObjectMetadata staged = stagingMetadata.get();
        String invalidReason = describeMismatch(media, staged);
        if (invalidReason != null) {
            repository.save(media.reject());
            throw new InvalidMediaObjectException(mediaId, invalidReason);
        }

        PromotionOutcome outcome =
                storage.promotePublic(mediaId, staged.etag(), staged.checksumSha256Base64(), media.contentType());

        return switch (outcome.status()) {
            case PROMOTED -> promote(media, outcome.publicObjectKey(), outcome.publicEtag(), now);
            case SOURCE_CHANGED -> {
                repository.save(media.reject());
                throw new MediaUploadChangedException(mediaId);
            }
            case TARGET_ALREADY_EXISTS -> recoverOrConflict(media, mediaId, staged, outcome, now);
        };
    }

    private ProductMedia promote(ProductMedia media, String publicObjectKey, String publicEtag, Instant readyAt) {
        ProductMedia ready = media.promoteToReady(publicObjectKey, publicEtag, readyAt);
        repository.save(ready);
        storage.deleteStagingObjectBestEffort(media.mediaId());
        return ready;
    }

    private ProductMedia recoverOrConflict(
            ProductMedia media, UUID mediaId, S3ObjectMetadata staged, PromotionOutcome outcome, Instant readyAt) {
        S3ObjectMetadata existing = outcome.existingPublicObjectMetadata().orElseThrow();
        if (matches(staged, existing)) {
            ProductMedia ready = media.promoteToReady(outcome.publicObjectKey(), existing.etag(), readyAt);
            repository.save(ready);
            storage.deleteStagingObjectBestEffort(mediaId);
            return ready;
        }
        repository.save(media.reject());
        throw new MediaPublishConflictException(mediaId);
    }

    private static boolean matches(S3ObjectMetadata staged, S3ObjectMetadata existingPublic) {
        return staged.checksumSha256Base64().equals(existingPublic.checksumSha256Base64())
                && staged.byteSize() == existingPublic.byteSize()
                && staged.contentType().equalsIgnoreCase(existingPublic.contentType());
    }

    private static String describeMismatch(ProductMedia media, S3ObjectMetadata staged) {
        if (!media.contentType().mimeType().equalsIgnoreCase(staged.contentType())) {
            return "Content-Type이 선언값과 다릅니다. 선언=" + media.contentType().mimeType() + ", 실제=" + staged.contentType();
        }
        if (staged.byteSize() != media.byteSize()) {
            return "크기가 선언값과 다릅니다. 선언=" + media.byteSize() + ", 실제=" + staged.byteSize();
        }
        if (staged.byteSize() > ProductMedia.MAX_BYTE_SIZE) {
            return "크기가 최대 허용치를 초과합니다. 실제=" + staged.byteSize();
        }
        if (!media.checksumSha256().equals(staged.checksumSha256Base64())) {
            return "Checksum이 선언값과 다릅니다";
        }
        return null;
    }
}
