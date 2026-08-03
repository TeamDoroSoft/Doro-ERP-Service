package com.dorosoft.erp.catalog.infrastructure.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dorosoft.erp.catalog.application.port.PresignedUpload;
import com.dorosoft.erp.catalog.application.port.PromotionOutcome;
import com.dorosoft.erp.catalog.application.port.S3ObjectMetadata;
import com.dorosoft.erp.catalog.domain.media.MediaContentType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * LocalStack S3에 대해 ADR-007의 조건부 승격 계약을 실제 API 호출로 검증한다(TBD 종결안: S3 통합 테스트는 LocalStack 사용).
 * 운영 Bucket·인증정보는 사용하지 않는다.
 */
@Testcontainers
@DisplayName("S3ProductObjectStorageAdapter LocalStack 통합 테스트(ADR-007)")
class S3ProductObjectStorageAdapterIntegrationTest {

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    private static final String BUCKET = "test-product-media";
    private static final String TENANT_ID = "test-tenant";

    private S3Client s3Client;
    private S3Presigner presigner;
    private S3ProductObjectStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        StaticCredentialsProvider credentials =
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
        Region region = Region.of(LOCALSTACK.getRegion());

        s3Client =
                S3Client.builder()
                        .endpointOverride(LOCALSTACK.getEndpoint())
                        .credentialsProvider(credentials)
                        .region(region)
                        .forcePathStyle(true)
                        .build();
        presigner =
                S3Presigner.builder()
                        .endpointOverride(LOCALSTACK.getEndpoint())
                        .credentialsProvider(credentials)
                        .region(region)
                        .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        ProductMediaProperties properties =
                new ProductMediaProperties(TENANT_ID, BUCKET, region.id(), "media.test.example");
        adapter = new S3ProductObjectStorageAdapter(s3Client, presigner, properties);
    }

    @AfterEach
    void tearDown() {
        List<S3Object> objects = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build()).contents();
        for (S3Object object : objects) {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(object.key()).build());
        }
        s3Client.close();
        presigner.close();
    }

    @Test
    @DisplayName("createStagingUpload은 업체 Prefix Key와 필수 Header를 포함한 Presigned URL을 발급한다")
    void createsStagingUploadWithTenantPrefixedKey() {
        UUID mediaId = UUID.randomUUID();
        String checksum = validChecksum("hello");

        PresignedUpload upload = adapter.createStagingUpload(mediaId, MediaContentType.WEBP, checksum);

        assertThat(upload.stagingObjectKey())
                .isEqualTo("tenants/" + TENANT_ID + "/catalog/staging/" + mediaId + "/source");
        assertThat(upload.uploadUrl()).contains(upload.stagingObjectKey());
        assertThat(upload.requiredHeaders()).containsEntry("Content-Type", "image/webp");
        assertThat(upload.requiredHeaders()).containsEntry("x-amz-checksum-sha256", checksum);
        assertThat(upload.expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Presigned URL로 실제 PUT하면 Staging Object가 만들어지고 실제 Byte로 Checksum을 재계산한다")
    void presignedUrlAcceptsRealPut() throws Exception {
        UUID mediaId = UUID.randomUUID();
        byte[] content = "hello-world".getBytes(StandardCharsets.UTF_8);
        String checksum = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(content));

        PresignedUpload upload = adapter.createStagingUpload(mediaId, MediaContentType.WEBP, checksum);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder requestBuilder =
                HttpRequest.newBuilder(URI.create(upload.uploadUrl())).PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        upload.requiredHeaders().forEach(requestBuilder::header);
        HttpResponse<Void> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);

        Optional<S3ObjectMetadata> inspected = adapter.inspectStagingObject(mediaId);
        assertThat(inspected).isPresent();
        assertThat(inspected.orElseThrow().checksumSha256Base64()).isEqualTo(checksum);
        assertThat(inspected.orElseThrow().byteSize()).isEqualTo(content.length);
        assertThat(inspected.orElseThrow().contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("inspectStagingObject는 없는 Key에 빈 값을 반환한다")
    void inspectReturnsEmptyForMissingKey() {
        assertThat(adapter.inspectStagingObject(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("promotePublic은 Public Key가 없으면 Source ETag 일치 조건으로 복사해 승격한다")
    void promotesWhenTargetAbsent() {
        UUID mediaId = UUID.randomUUID();
        byte[] content = "promote-me".getBytes(StandardCharsets.UTF_8);
        String checksum = putStagingObject(mediaId, content);
        S3ObjectMetadata staged = adapter.inspectStagingObject(mediaId).orElseThrow();

        PromotionOutcome outcome = adapter.promotePublic(mediaId, staged.etag(), checksum, MediaContentType.WEBP);

        assertThat(outcome.status()).isEqualTo(PromotionOutcome.PromotionStatus.PROMOTED);
        assertThat(outcome.publicObjectKey()).contains(TENANT_ID).contains(mediaId.toString());
        assertThat(outcome.publicEtag()).isNotBlank();

        GetObjectResponse publicMetadata =
                s3Client.getObject(GetObjectRequest.builder().bucket(BUCKET).key(outcome.publicObjectKey()).build()).response();
        assertThat(publicMetadata.contentLength()).isEqualTo(content.length);
    }

    @Test
    @DisplayName("promotePublic은 Source ETag가 다르면 SOURCE_CHANGED를 반환하고 Public Object를 만들지 않는다")
    void reportsSourceChangedWhenEtagMismatches() {
        UUID mediaId = UUID.randomUUID();
        byte[] content = "etag-check".getBytes(StandardCharsets.UTF_8);
        String checksum = putStagingObject(mediaId, content);

        PromotionOutcome outcome = adapter.promotePublic(mediaId, "stale-etag-value", checksum, MediaContentType.WEBP);

        assertThat(outcome.status()).isEqualTo(PromotionOutcome.PromotionStatus.SOURCE_CHANGED);
        String expectedPublicKey = ProductMediaKeys.publicKey(TENANT_ID, mediaId, checksum, MediaContentType.WEBP);
        assertThat(objectExists(expectedPublicKey)).isFalse();
    }

    @Test
    @DisplayName("promotePublic은 Public Key가 이미 있으면 다시 승격을 시도하지 않고 기존 Metadata를 반환한다")
    void reportsTargetAlreadyExists() {
        UUID mediaId = UUID.randomUUID();
        byte[] content = "already-there".getBytes(StandardCharsets.UTF_8);
        String checksum = putStagingObject(mediaId, content);
        S3ObjectMetadata staged = adapter.inspectStagingObject(mediaId).orElseThrow();

        PromotionOutcome first = adapter.promotePublic(mediaId, staged.etag(), checksum, MediaContentType.WEBP);
        assertThat(first.status()).isEqualTo(PromotionOutcome.PromotionStatus.PROMOTED);

        PromotionOutcome second = adapter.promotePublic(mediaId, staged.etag(), checksum, MediaContentType.WEBP);

        assertThat(second.status()).isEqualTo(PromotionOutcome.PromotionStatus.TARGET_ALREADY_EXISTS);
        assertThat(second.existingPublicObjectMetadata()).isPresent();
        assertThat(second.existingPublicObjectMetadata().orElseThrow().checksumSha256Base64()).isEqualTo(checksum);
        assertThat(second.existingPublicObjectMetadata().orElseThrow().byteSize()).isEqualTo(content.length);
    }

    @Test
    @DisplayName("deleteStagingObjectBestEffort는 Staging Object를 삭제하고 존재하지 않아도 예외를 던지지 않는다")
    void deletesStagingObject() {
        UUID mediaId = UUID.randomUUID();
        putStagingObject(mediaId, "delete-me".getBytes(StandardCharsets.UTF_8));

        adapter.deleteStagingObjectBestEffort(mediaId);

        assertThat(adapter.inspectStagingObject(mediaId)).isEmpty();
        assertThatCode(() -> adapter.deleteStagingObjectBestEffort(mediaId)).doesNotThrowAnyException();
    }

    private boolean objectExists(String key) {
        try (var response = s3Client.getObject(GetObjectRequest.builder().bucket(BUCKET).key(key).build())) {
            return true;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            return false;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private String putStagingObject(UUID mediaId, byte[] content) {
        try {
            String checksum = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(content));
            String key = "tenants/" + TENANT_ID + "/catalog/staging/" + mediaId + "/source";
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(BUCKET).key(key).contentType("image/webp").build(),
                    RequestBody.fromBytes(content));
            return checksum;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String validChecksum(String seed) {
        try {
            return Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
