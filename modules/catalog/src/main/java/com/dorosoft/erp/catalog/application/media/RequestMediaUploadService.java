package com.dorosoft.erp.catalog.application.media;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.application.port.PresignedUpload;
import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.application.port.ProductObjectStorage;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyKeyReusedException;
import com.dorosoft.erp.catalog.domain.idempotency.IdempotencyRequestHash;
import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import com.dorosoft.erp.catalog.domain.media.Sha256Checksums;
import com.dorosoft.erp.catalog.domain.media.UnsupportedMediaContentTypeException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staging Presigned PUT URL 발급(ADR-007). 형식·크기·Checksum 검증을 통과해야 S3에 URL을 요청한다.
 * Idempotency-Key 재요청은 같은 내용이면 같은 mediaId에 새로 서명한 URL을, 다른 내용이면 거부를
 * 반환한다(생성 API 공통 계약). Presigned URL은 5분 TTL이라 재요청마다 새로 서명해야 실제로 쓸 수 있다.
 */
@Service
public class RequestMediaUploadService {

    private final ProductMediaRepository mediaRepository;
    private final CatalogRevisionRepository catalogRevisionRepository;
    private final ProductObjectStorage storage;

    public RequestMediaUploadService(
            ProductMediaRepository mediaRepository,
            CatalogRevisionRepository catalogRevisionRepository,
            ProductObjectStorage storage) {
        this.mediaRepository = mediaRepository;
        this.catalogRevisionRepository = catalogRevisionRepository;
        this.storage = storage;
    }

    @Transactional
    public MediaUploadTicket requestUpload(RequestMediaUploadCommand command) {
        MediaContentType contentType =
                MediaContentType.fromMimeType(command.contentType())
                        .orElseThrow(() -> new UnsupportedMediaContentTypeException(command.contentType()));
        ProductMedia.requireValidByteSize(command.byteSize());
        Sha256Checksums.requireValidBase64(command.checksumSha256Base64());

        String requestHash =
                IdempotencyRequestHash.of(command.contentType(), command.byteSize(), command.checksumSha256Base64());

        if (command.idempotencyKey() != null) {
            ProductMedia existing = mediaRepository.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
            if (existing != null) {
                if (!requestHash.equals(existing.idempotencyRequestHash())) {
                    throw new IdempotencyKeyReusedException(command.idempotencyKey());
                }
                PresignedUpload refreshed =
                        storage.createStagingUpload(existing.mediaId(), existing.contentType(), existing.checksumSha256());
                return new MediaUploadTicket(
                        existing.mediaId(), refreshed.uploadUrl(), refreshed.requiredHeaders(), refreshed.expiresAt());
            }
        }

        UUID catalogId =
                catalogRevisionRepository
                        .findCurrent()
                        .orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"))
                        .catalogId();

        UUID mediaId = UUID.randomUUID();
        PresignedUpload upload = storage.createStagingUpload(mediaId, contentType, command.checksumSha256Base64());

        ProductMedia media =
                ProductMedia.stage(
                        mediaId,
                        catalogId,
                        upload.stagingObjectKey(),
                        contentType,
                        command.byteSize(),
                        command.checksumSha256Base64(),
                        command.requestedBy(),
                        Instant.now(),
                        command.idempotencyKey(),
                        requestHash);
        mediaRepository.save(media);

        return new MediaUploadTicket(mediaId, upload.uploadUrl(), upload.requiredHeaders(), upload.expiresAt());
    }
}
