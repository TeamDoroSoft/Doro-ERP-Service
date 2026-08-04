package com.dorosoft.erp.catalog.application.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.catalog.domain.media.MediaStatus;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import com.dorosoft.erp.catalog.domain.media.UnsupportedMediaContentTypeException;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestMediaUploadService - Staging 업로드 준비(ADR-007)")
class RequestMediaUploadServiceTest {

    private static final String VALID_CHECKSUM = validChecksum();

    private FakeProductMediaRepository mediaRepository;
    private FakeCatalogRevisionRepository revisionRepository;
    private FakeProductObjectStorage storage;
    private RequestMediaUploadService service;
    private UUID catalogId;

    @BeforeEach
    void setUp() {
        catalogId = UUID.randomUUID();
        mediaRepository = new FakeProductMediaRepository();
        revisionRepository = new FakeCatalogRevisionRepository(new CatalogRevision(catalogId, 0L, Instant.now()));
        storage = new FakeProductObjectStorage();
        service = new RequestMediaUploadService(mediaRepository, revisionRepository, storage);
    }

    private RequestMediaUploadCommand validCommand() {
        return new RequestMediaUploadCommand("image/webp", 1024, VALID_CHECKSUM, UUID.randomUUID(), null);
    }

    @Test
    @DisplayName("허용되지 않는 Content-Type은 Media Row를 만들기 전에 거부한다")
    void rejectsDisallowedContentType() {
        RequestMediaUploadCommand command = new RequestMediaUploadCommand("image/svg+xml", 1024, VALID_CHECKSUM, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.requestUpload(command))
                .isInstanceOf(UnsupportedMediaContentTypeException.class);
        assertThat(mediaRepository.findExpiredPending(Instant.now().plusSeconds(1), 10)).isEmpty();
    }

    @Test
    @DisplayName("5MB를 초과하는 byteSize는 거부한다")
    void rejectsOversizedByteSize() {
        RequestMediaUploadCommand command =
                new RequestMediaUploadCommand("image/webp", ProductMedia.MAX_BYTE_SIZE + 1, VALID_CHECKSUM, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.requestUpload(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효하지 않은 Checksum 형식은 거부한다")
    void rejectsInvalidChecksumFormat() {
        RequestMediaUploadCommand command = new RequestMediaUploadCommand("image/webp", 1024, "not-base64", UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.requestUpload(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Catalog가 초기화되지 않았으면 IllegalStateException")
    void throwsWhenCatalogNotBootstrapped() {
        revisionRepository = new FakeCatalogRevisionRepository(null);
        service = new RequestMediaUploadService(mediaRepository, revisionRepository, storage);

        assertThatThrownBy(() -> service.requestUpload(validCommand())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("검증을 통과하면 PENDING Media를 저장하고 업로드 Ticket을 반환한다")
    void createsPendingMediaAndReturnsTicket() {
        UUID requestedBy = UUID.randomUUID();
        RequestMediaUploadCommand command = new RequestMediaUploadCommand("image/webp", 2048, VALID_CHECKSUM, requestedBy, null);

        MediaUploadTicket ticket = service.requestUpload(command);

        assertThat(ticket.mediaId()).isNotNull();
        assertThat(ticket.uploadUrl()).isNotBlank();
        assertThat(ticket.requiredHeaders()).containsEntry("Content-Type", "image/webp");
        assertThat(ticket.requiredHeaders()).containsEntry("x-amz-checksum-sha256", VALID_CHECKSUM);
        assertThat(ticket.expiresAt()).isAfter(Instant.now());

        ProductMedia saved = mediaRepository.findById(ticket.mediaId()).orElseThrow();
        assertThat(saved.status()).isEqualTo(MediaStatus.PENDING);
        assertThat(saved.catalogId()).isEqualTo(catalogId);
        assertThat(saved.byteSize()).isEqualTo(2048);
        assertThat(saved.checksumSha256()).isEqualTo(VALID_CHECKSUM);
        assertThat(saved.createdBy()).isEqualTo(requestedBy);
    }

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 내용의 재요청은 같은 mediaId에 새로 서명한 URL을 반환한다")
    void idempotentRetryWithSameBodyReusesMediaId() {
        String key = "idem-media-1";
        RequestMediaUploadCommand command = new RequestMediaUploadCommand("image/webp", 1024, VALID_CHECKSUM, UUID.randomUUID(), key);

        MediaUploadTicket first = service.requestUpload(command);
        MediaUploadTicket retried = service.requestUpload(command);

        assertThat(retried.mediaId()).isEqualTo(first.mediaId());
        assertThat(mediaRepository.findExpiredPending(Instant.now().plusSeconds(1), 10)).hasSize(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 내용으로 재요청하면 IdempotencyKeyReusedException")
    void idempotentRetryWithDifferentBodyFails() {
        String key = "idem-media-2";
        service.requestUpload(new RequestMediaUploadCommand("image/webp", 1024, VALID_CHECKSUM, UUID.randomUUID(), key));

        RequestMediaUploadCommand differentBody =
                new RequestMediaUploadCommand("image/png", 1024, VALID_CHECKSUM, UUID.randomUUID(), key);
        assertThatThrownBy(() -> service.requestUpload(differentBody))
                .isInstanceOf(com.dorosoft.erp.catalog.domain.idempotency.IdempotencyKeyReusedException.class);
    }

    private static String validChecksum() {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest("product-media".getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
