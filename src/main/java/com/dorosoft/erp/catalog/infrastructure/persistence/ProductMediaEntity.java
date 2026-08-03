package com.dorosoft.erp.catalog.infrastructure.persistence;

import com.dorosoft.erp.catalog.domain.media.MediaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 상품 이미지 Media Metadata의 JPA 매핑(ADR-007). 낙관적 잠금 대신 완료 처리 동안
 * Pessimistic Lock(SELECT FOR UPDATE)으로 동시 완료 요청을 막는다.
 */
@Entity
@Table(name = "product_media")
class ProductMediaEntity {

    @Id
    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "catalog_id", nullable = false)
    private UUID catalogId;

    @Column(name = "staging_object_key", nullable = false, unique = true, length = 512)
    private String stagingObjectKey;

    @Column(name = "published_object_key", unique = true, length = 512)
    private String publishedObjectKey;

    @Column(name = "object_etag", length = 128)
    private String objectEtag;

    @Column(name = "checksum_sha256", nullable = false, length = 44)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MediaStatus status;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "upload_expires_at", nullable = false)
    private Instant uploadExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    protected ProductMediaEntity() {}

    ProductMediaEntity(
            UUID mediaId,
            UUID catalogId,
            String stagingObjectKey,
            String publishedObjectKey,
            String objectEtag,
            String checksumSha256,
            MediaStatus status,
            String contentType,
            long byteSize,
            UUID createdBy,
            Instant uploadExpiresAt,
            Instant createdAt,
            Instant readyAt) {
        this.mediaId = mediaId;
        this.catalogId = catalogId;
        this.stagingObjectKey = stagingObjectKey;
        this.publishedObjectKey = publishedObjectKey;
        this.objectEtag = objectEtag;
        this.checksumSha256 = checksumSha256;
        this.status = status;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.createdBy = createdBy;
        this.uploadExpiresAt = uploadExpiresAt;
        this.createdAt = createdAt;
        this.readyAt = readyAt;
    }

    /** 상태 전이 결과(READY·REJECTED)를 그대로 반영한다. staging/checksum/생성 정보는 바뀌지 않는다. */
    void applyTransition(String publishedObjectKey, String objectEtag, MediaStatus status, Instant readyAt) {
        this.publishedObjectKey = publishedObjectKey;
        this.objectEtag = objectEtag;
        this.status = status;
        this.readyAt = readyAt;
    }

    UUID getMediaId() {
        return mediaId;
    }

    UUID getCatalogId() {
        return catalogId;
    }

    String getStagingObjectKey() {
        return stagingObjectKey;
    }

    String getPublishedObjectKey() {
        return publishedObjectKey;
    }

    String getObjectEtag() {
        return objectEtag;
    }

    String getChecksumSha256() {
        return checksumSha256;
    }

    MediaStatus getStatus() {
        return status;
    }

    String getContentType() {
        return contentType;
    }

    long getByteSize() {
        return byteSize;
    }

    UUID getCreatedBy() {
        return createdBy;
    }

    Instant getUploadExpiresAt() {
        return uploadExpiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getReadyAt() {
        return readyAt;
    }
}
