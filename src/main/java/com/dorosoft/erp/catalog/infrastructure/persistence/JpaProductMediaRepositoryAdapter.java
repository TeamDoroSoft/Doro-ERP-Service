package com.dorosoft.erp.catalog.infrastructure.persistence;

import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** JPA 엔티티와 도메인 ProductMedia 사이의 변환을 전담한다. 엔티티는 이 패키지 밖으로 나가지 않는다. */
@Repository
public class JpaProductMediaRepositoryAdapter implements ProductMediaRepository {

    private final ProductMediaJpaRepository jpaRepository;

    public JpaProductMediaRepositoryAdapter(ProductMediaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ProductMedia> findById(UUID mediaId) {
        return jpaRepository.findById(mediaId).map(JpaProductMediaRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<ProductMedia> findByIdForUpdate(UUID mediaId) {
        return jpaRepository.findByIdForUpdate(mediaId).map(JpaProductMediaRepositoryAdapter::toDomain);
    }

    @Override
    public ProductMedia save(ProductMedia media) {
        ProductMediaEntity entity = jpaRepository.findById(media.mediaId()).orElse(null);
        if (entity == null) {
            jpaRepository.save(toEntity(media));
        } else {
            entity.applyTransition(media.publishedObjectKey(), media.objectEtag(), media.status(), media.readyAt());
        }
        return media;
    }

    @Override
    public List<ProductMedia> findExpiredPending(Instant threshold, int limit) {
        return jpaRepository
                .findByStatusAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                        com.dorosoft.erp.catalog.domain.media.MediaStatus.PENDING, threshold, PageRequest.of(0, limit))
                .stream()
                .map(JpaProductMediaRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<ProductMedia> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(JpaProductMediaRepositoryAdapter::toDomain);
    }

    private static ProductMediaEntity toEntity(ProductMedia media) {
        return new ProductMediaEntity(
                media.mediaId(),
                media.catalogId(),
                media.stagingObjectKey(),
                media.publishedObjectKey(),
                media.objectEtag(),
                media.checksumSha256(),
                media.status(),
                media.contentType().mimeType(),
                media.byteSize(),
                media.createdBy(),
                media.uploadExpiresAt(),
                media.createdAt(),
                media.readyAt(),
                media.idempotencyKey(),
                media.idempotencyRequestHash());
    }

    private static ProductMedia toDomain(ProductMediaEntity entity) {
        MediaContentType contentType =
                MediaContentType.fromMimeType(entity.getContentType())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "저장된 content_type이 허용 목록에 없습니다: " + entity.getContentType()));
        return new ProductMedia(
                entity.getMediaId(),
                entity.getCatalogId(),
                entity.getStagingObjectKey(),
                entity.getPublishedObjectKey(),
                entity.getObjectEtag(),
                entity.getChecksumSha256(),
                entity.getStatus(),
                contentType,
                entity.getByteSize(),
                entity.getCreatedBy(),
                entity.getUploadExpiresAt(),
                entity.getCreatedAt(),
                entity.getReadyAt(),
                entity.getIdempotencyKey(),
                entity.getIdempotencyRequestHash());
    }
}
