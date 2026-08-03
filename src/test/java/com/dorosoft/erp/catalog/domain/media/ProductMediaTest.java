package com.dorosoft.erp.catalog.domain.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProductMedia 상태 전이(ADR-007)")
class ProductMediaTest {

    private static final UUID MEDIA_ID = UUID.randomUUID();
    private static final UUID CATALOG_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();
    private static final String CHECKSUM = "N4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=";

    private static ProductMedia staged(Instant createdAt) {
        return ProductMedia.stage(
                MEDIA_ID, CATALOG_ID, "tenants/t/catalog/staging/" + MEDIA_ID + "/source",
                MediaContentType.WEBP, 1024, CHECKSUM, CREATED_BY, createdAt, null, null);
    }

    @Nested
    @DisplayName("stage")
    class Stage {

        @Test
        @DisplayName("PENDING으로 생성되고 Presigned URL 만료 시각은 생성 후 5분이다")
        void createsPendingWithFiveMinuteExpiry() {
            Instant now = Instant.now();
            ProductMedia media = staged(now);

            assertThat(media.status()).isEqualTo(MediaStatus.PENDING);
            assertThat(media.isPending()).isTrue();
            assertThat(media.publishedObjectKey()).isNull();
            assertThat(media.objectEtag()).isNull();
            assertThat(media.readyAt()).isNull();
            assertThat(media.uploadExpiresAt()).isEqualTo(now.plus(ProductMedia.PRESIGNED_UPLOAD_TTL));
        }

        @Test
        @DisplayName("5MB를 초과하면 IllegalArgumentException")
        void rejectsOversizedByteSize() {
            assertThatThrownBy(
                            () ->
                                    ProductMedia.stage(
                                            MEDIA_ID, CATALOG_ID, "key", MediaContentType.WEBP,
                                            ProductMedia.MAX_BYTE_SIZE + 1, CHECKSUM, CREATED_BY, Instant.now(), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("음수 byteSize는 IllegalArgumentException")
        void rejectsNegativeByteSize() {
            assertThatThrownBy(
                            () ->
                                    ProductMedia.stage(
                                            MEDIA_ID, CATALOG_ID, "key", MediaContentType.WEBP,
                                            -1, CHECKSUM, CREATED_BY, Instant.now(), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("promoteToReady")
    class PromoteToReady {

        @Test
        @DisplayName("PENDING에서 READY로 전이하고 Public Key·ETag·완료 시각을 채운다")
        void promotesFromPending() {
            ProductMedia media = staged(Instant.now());
            Instant readyAt = Instant.now();

            ProductMedia ready = media.promoteToReady("tenants/t/catalog/public/x/hash.webp", "etag-1", readyAt);

            assertThat(ready.status()).isEqualTo(MediaStatus.READY);
            assertThat(ready.isReady()).isTrue();
            assertThat(ready.publishedObjectKey()).isEqualTo("tenants/t/catalog/public/x/hash.webp");
            assertThat(ready.objectEtag()).isEqualTo("etag-1");
            assertThat(ready.readyAt()).isEqualTo(readyAt);
            // 원본은 불변이다.
            assertThat(media.status()).isEqualTo(MediaStatus.PENDING);
        }

        @Test
        @DisplayName("PENDING이 아니면 IllegalStateException")
        void rejectsWhenNotPending() {
            ProductMedia ready =
                    staged(Instant.now()).promoteToReady("key", "etag", Instant.now());

            assertThatThrownBy(() -> ready.promoteToReady("key2", "etag2", Instant.now()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("PENDING에서 REJECTED로 전이하고 Public 관련 필드를 비운다")
        void rejectsFromPending() {
            ProductMedia rejected = staged(Instant.now()).reject();

            assertThat(rejected.status()).isEqualTo(MediaStatus.REJECTED);
            assertThat(rejected.isRejected()).isTrue();
            assertThat(rejected.publishedObjectKey()).isNull();
            assertThat(rejected.objectEtag()).isNull();
            assertThat(rejected.readyAt()).isNull();
        }

        @Test
        @DisplayName("READY에서는 IllegalStateException")
        void rejectsFromReadyIsInvalid() {
            ProductMedia ready = staged(Instant.now()).promoteToReady("key", "etag", Instant.now());

            assertThatThrownBy(ready::reject).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("isPendingExpired")
    class PendingExpired {

        @Test
        @DisplayName("생성 후 24시간 미만이면 만료가 아니다")
        void notExpiredWithinWindow() {
            Instant createdAt = Instant.now().minus(23, ChronoUnit.HOURS);
            ProductMedia media = staged(createdAt);

            assertThat(media.isPendingExpired(Instant.now())).isFalse();
        }

        @Test
        @DisplayName("생성 후 24시간이 지나면 만료다")
        void expiredAfterWindow() {
            Instant createdAt = Instant.now().minus(25, ChronoUnit.HOURS);
            ProductMedia media = staged(createdAt);

            assertThat(media.isPendingExpired(Instant.now())).isTrue();
        }

        @Test
        @DisplayName("READY·REJECTED는 만료 판정 대상이 아니다")
        void nonPendingIsNeverExpired() {
            Instant createdAt = Instant.now().minus(25, ChronoUnit.HOURS);
            ProductMedia ready = staged(createdAt).promoteToReady("key", "etag", Instant.now());
            ProductMedia rejected = staged(createdAt).reject();

            assertThat(ready.isPendingExpired(Instant.now())).isFalse();
            assertThat(rejected.isPendingExpired(Instant.now())).isFalse();
        }
    }
}
