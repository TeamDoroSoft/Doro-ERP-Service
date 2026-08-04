package com.dorosoft.erp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dorosoft.erp.testsupport.MySqlTestcontainersConfiguration;
import com.dorosoft.erp.catalog.application.port.ProductMediaRepository;
import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import com.dorosoft.erp.catalog.domain.media.MediaStatus;
import com.dorosoft.erp.catalog.domain.media.ProductMedia;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "doro.catalog.bootstrap.enabled=false")
@Import(MySqlTestcontainersConfiguration.class)
@DisplayName("ProductMedia 영속화 통합 테스트 - JPA Adapter가 실제 Schema와 왕복하는지 검증한다")
class ProductMediaPersistenceIntegrationTest {

    private static final String CHECKSUM = validChecksum();

    @Autowired private ProductMediaRepository repository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcClient jdbcClient;

    private UUID catalogId;

    @BeforeEach
    void 테이블을_비우고_부모행을_만든다() {
        CatalogIntegrationSupport.cleanCatalogTables(jdbcClient);
        catalogId = CatalogIntegrationSupport.insertCatalogRevision(jdbcClient);
    }

    private ProductMedia pendingMedia(Instant createdAt) {
        UUID mediaId = UUID.randomUUID();
        return ProductMedia.stage(
                mediaId,
                catalogId,
                "tenants/t/catalog/staging/" + mediaId + "/source",
                MediaContentType.WEBP,
                1024,
                CHECKSUM,
                UUID.randomUUID(),
                createdAt,
                null,
                null);
    }

    @Test
    @DisplayName("저장한 PENDING Media는 다시 조회해도 그대로다(Round-trip)")
    void savesAndFindsById() {
        ProductMedia media = pendingMedia(Instant.now());

        inTransaction(() -> repository.save(media));

        Optional<ProductMedia> found = inTransaction(() -> repository.findById(media.mediaId()));

        assertThat(found).isPresent();
        ProductMedia reloaded = found.orElseThrow();
        assertThat(reloaded.mediaId()).isEqualTo(media.mediaId());
        assertThat(reloaded.catalogId()).isEqualTo(catalogId);
        assertThat(reloaded.stagingObjectKey()).isEqualTo(media.stagingObjectKey());
        assertThat(reloaded.checksumSha256()).isEqualTo(CHECKSUM);
        assertThat(reloaded.status()).isEqualTo(MediaStatus.PENDING);
        assertThat(reloaded.contentType()).isEqualTo(MediaContentType.WEBP);
    }

    @Test
    @DisplayName("findByIdForUpdate로 잠근 행도 findById와 같은 값을 반환한다")
    void findByIdForUpdateReturnsSameData() {
        ProductMedia media = pendingMedia(Instant.now());
        inTransaction(() -> repository.save(media));

        Optional<ProductMedia> locked = inTransaction(() -> repository.findByIdForUpdate(media.mediaId()));

        assertThat(locked).isPresent();
        assertThat(locked.orElseThrow().mediaId()).isEqualTo(media.mediaId());
    }

    @Test
    @DisplayName("기존 Row에 저장하면 상태 전이 필드만 갱신된다")
    void updatesTransitionFieldsOnExistingRow() {
        ProductMedia media = pendingMedia(Instant.now());
        inTransaction(() -> repository.save(media));

        Instant readyAt = Instant.now();
        ProductMedia ready = media.promoteToReady("tenants/t/catalog/public/x/hash.webp", "etag-1", readyAt);
        inTransaction(() -> repository.save(ready));

        ProductMedia reloaded = inTransaction(() -> repository.findById(media.mediaId())).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(MediaStatus.READY);
        assertThat(reloaded.publishedObjectKey()).isEqualTo("tenants/t/catalog/public/x/hash.webp");
        assertThat(reloaded.objectEtag()).isEqualTo("etag-1");
        assertThat(reloaded.stagingObjectKey()).isEqualTo(media.stagingObjectKey());
        assertThat(reloaded.checksumSha256()).isEqualTo(CHECKSUM);
    }

    @Test
    @DisplayName("Idempotency-Key로 저장한 Media를 다시 조회할 수 있다")
    void savesAndFindsByIdempotencyKey() {
        UUID mediaId = UUID.randomUUID();
        ProductMedia media =
                ProductMedia.stage(
                        mediaId,
                        catalogId,
                        "tenants/t/catalog/staging/" + mediaId + "/source",
                        MediaContentType.WEBP,
                        1024,
                        CHECKSUM,
                        UUID.randomUUID(),
                        Instant.now(),
                        "idem-media-persistence",
                        "hash-value");

        inTransaction(() -> repository.save(media));

        Optional<ProductMedia> found = inTransaction(() -> repository.findByIdempotencyKey("idem-media-persistence"));

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().mediaId()).isEqualTo(mediaId);
        assertThat(found.orElseThrow().idempotencyRequestHash()).isEqualTo("hash-value");
        assertThat(inTransaction(() -> repository.findByIdempotencyKey("no-such-key"))).isEmpty();
    }

    @Test
    @DisplayName("findExpiredPending은 임계값보다 오래된 PENDING만 오래된 순으로 반환한다")
    void findExpiredPendingReturnsOnlyOlderThanThreshold() {
        ProductMedia old = pendingMedia(Instant.now().minus(25, ChronoUnit.HOURS));
        ProductMedia fresh = pendingMedia(Instant.now().minus(1, ChronoUnit.HOURS));
        ProductMedia readyOld =
                pendingMedia(Instant.now().minus(30, ChronoUnit.HOURS))
                        .promoteToReady("tenants/t/catalog/public/y/hash.webp", "etag-2", Instant.now());
        inTransaction(
                () -> {
                    repository.save(old);
                    repository.save(fresh);
                    repository.save(readyOld);
                    return null;
                });

        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);
        List<ProductMedia> expired = inTransaction(() -> repository.findExpiredPending(threshold, 10));

        assertThat(expired).extracting(ProductMedia::mediaId).containsExactly(old.mediaId());
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private static String validChecksum() {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest("persistence".getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
