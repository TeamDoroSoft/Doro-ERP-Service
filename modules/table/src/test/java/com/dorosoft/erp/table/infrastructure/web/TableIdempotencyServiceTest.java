package com.dorosoft.erp.table.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dorosoft.erp.table.application.TableErrorCode;
import com.dorosoft.erp.table.application.TableManagementException;
import com.dorosoft.erp.table.application.idempotency.UnavailableTableIdempotencyResponseCrypto;
import com.dorosoft.erp.table.application.port.TableIdempotencyResponseCrypto;
import com.dorosoft.erp.table.infrastructure.persistence.TableIdempotencyRecordEntity;
import com.dorosoft.erp.table.infrastructure.persistence.TableIdempotencyRecordJpaRepository;
import com.dorosoft.erp.table.infrastructure.persistence.crypto.AesGcmTableIdempotencyResponseCrypto;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TABLE-10 인수·보안 검증: {@link TableIdempotencyService}가 응답을 저장·재생할 때 평문을 남기지 않고,
 * Key가 없거나 저장 값이 손상된 경우 안전하게 실패하는지에 대한 단위 테스트. DB나 Docker 없이
 * {@link TableIdempotencyRecordJpaRepository}를 Mockito로 대체해 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TableIdempotencyServiceTest {

    private static final String QR_ACCESS_URL_SECRET = "https://qr.example.test/qr#token=TOP-SECRET-TOKEN";

    @Mock private TableIdempotencyRecordJpaRepository repository;
    @Mock private HttpServletRequest request;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TableIdempotencyResponseCrypto crypto =
            new AesGcmTableIdempotencyResponseCrypto(key((byte) 42));

    private TableIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new TableIdempotencyService(repository, objectMapper, crypto);
        lenient().when(request.getMethod()).thenReturn("POST");
        lenient().when(request.getRequestURI()).thenReturn("/tables/table-1/qr-credentials");
    }

    private record QrIssueResponse(String credentialId, String accessUrl) {}

    private record QrReplayResponse(String credentialId) {}

    @Test
    @DisplayName("최초 요청 저장 시 response_body는 암호화되어 원본 JSON 평문을 포함하지 않는다")
    void firstRequest_storesEncryptedResponseBodyWithoutPlaintext() {
        when(repository.findById("key-1")).thenReturn(Optional.empty());

        service.execute(
                "key-1",
                request,
                Map.of(),
                () -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new QrIssueResponse("cred-1", QR_ACCESS_URL_SECRET)));

        ArgumentCaptor<TableIdempotencyRecordEntity> captor =
                ArgumentCaptor.forClass(TableIdempotencyRecordEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        String storedResponseBody = captor.getValue().getResponseBody();

        assertTrue(storedResponseBody.startsWith("v1:"), "stored value must use the documented envelope format");
        assertFalse(storedResponseBody.contains(QR_ACCESS_URL_SECRET),
                "stored value must not contain the plaintext access URL/token");
        assertFalse(storedResponseBody.contains("cred-1"), "stored value must not contain any plaintext JSON field");
    }

    @Test
    @DisplayName("동일 멱등 키로 재요청하면 저장된 값을 복호화해 원래 응답과 동일하게 재생하고, 원래 작업을 다시 실행하지 않는다")
    void replay_decryptsToTheOriginalResponseWithoutReRunningTheOperation() {
        AtomicInteger invocationCount = new AtomicInteger();
        Supplier<ResponseEntity<?>> operation = () -> {
            invocationCount.incrementAndGet();
            return ResponseEntity.status(HttpStatus.CREATED).body(new QrIssueResponse("cred-2", "ignored"));
        };

        when(repository.findById("key-2")).thenReturn(Optional.empty());
        ArgumentCaptor<TableIdempotencyRecordEntity> captor =
                ArgumentCaptor.forClass(TableIdempotencyRecordEntity.class);
        service.execute("key-2", request, Map.of(), operation);
        verify(repository).saveAndFlush(captor.capture());

        when(repository.findById("key-2")).thenReturn(Optional.of(captor.getValue()));
        ResponseEntity<Object> replayed = service.execute("key-2", request, Map.of(), operation);

        assertEquals(1, invocationCount.get(), "a replayed request must not re-run the original operation");
        assertEquals(HttpStatus.CREATED, replayed.getStatusCode());
        JsonNode body = (JsonNode) replayed.getBody();
        assertEquals("cred-2", body.get("credentialId").asText());
    }

    @Test
    @DisplayName("QR 발급의 replayWithoutSecret() 재생 응답은 암호화 이후에도 Token/접근 URL 원문을 포함하지 않는다")
    void qrCredentialReplayBody_neverPersistsTheAccessUrlEvenEncrypted() {
        when(repository.findById("key-3")).thenReturn(Optional.empty());
        ArgumentCaptor<TableIdempotencyRecordEntity> captor =
                ArgumentCaptor.forClass(TableIdempotencyRecordEntity.class);

        // Mirrors TableQrCredentialController: the full response (with accessUrl) is returned to
        // the caller, but only the redacted QrReplayResponse-equivalent is ever persisted.
        service.execute(
                "key-3",
                request,
                Map.of(),
                () -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new QrIssueResponse("cred-3", QR_ACCESS_URL_SECRET)),
                response -> new QrReplayResponse(((QrIssueResponse) response.getBody()).credentialId()));

        verify(repository).saveAndFlush(captor.capture());
        String storedResponseBody = captor.getValue().getResponseBody();
        String decrypted = crypto.decrypt(storedResponseBody);

        assertFalse(decrypted.contains(QR_ACCESS_URL_SECRET),
                "the redacted replay body must not contain the access URL/token even after decrypting it back");
        assertTrue(decrypted.contains("cred-3"), "the non-secret credential id is still expected to be present");
    }

    @Test
    @DisplayName("서로 다른 멱등 키로 동일한 응답을 저장해도 IV가 달라 암호화 결과는 서로 다르다")
    void differentIdempotencyKeys_produceDifferentCiphertextForTheSameResponse() {
        when(repository.findById("key-4")).thenReturn(Optional.empty());
        when(repository.findById("key-5")).thenReturn(Optional.empty());
        Supplier<ResponseEntity<?>> operation =
                () -> ResponseEntity.status(HttpStatus.CREATED).body(new QrIssueResponse("cred-x", "same-secret"));

        ArgumentCaptor<TableIdempotencyRecordEntity> captor =
                ArgumentCaptor.forClass(TableIdempotencyRecordEntity.class);
        service.execute("key-4", request, Map.of(), operation);
        service.execute("key-5", request, Map.of(), operation);
        verify(repository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());

        assertNotEquals(
                captor.getAllValues().get(0).getResponseBody(),
                captor.getAllValues().get(1).getResponseBody());
    }

    @Test
    @DisplayName("암호화 Key가 구성되지 않으면 멱등 저장은 안전하게 503으로 실패하고 평문을 저장하지 않는다")
    void whenCryptoIsUnavailable_storingFailsSafelyInsteadOfPersistingPlaintext() {
        TableIdempotencyService unavailableService =
                new TableIdempotencyService(repository, objectMapper, new UnavailableTableIdempotencyResponseCrypto());
        when(repository.findById("key-6")).thenReturn(Optional.empty());

        TableManagementException exception = assertThrows(
                TableManagementException.class,
                () -> unavailableService.execute(
                        "key-6",
                        request,
                        Map.of(),
                        () -> ResponseEntity.status(HttpStatus.CREATED).body(new QrIssueResponse("cred-6", "secret"))));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertEquals(TableErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE, exception.code());
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("저장된 response_body가 손상되었거나 변조되었으면 재생 시 평문 대신 안전하게 실패한다")
    void whenStoredResponseBodyIsCorrupted_replayFailsSafely() {
        // The replay path first compares method/path/request-hash before ever attempting to
        // decrypt, so the stored hash must match this test's empty Map.of() request body for the
        // corrupted response_body itself to actually be reached.
        TableIdempotencyRecordEntity corrupted = new TableIdempotencyRecordEntity(
                "key-7",
                "POST",
                "/tables/table-1/qr-credentials",
                requestHashOf(Map.of()),
                201,
                "not-a-valid-envelope",
                Instant.now());
        when(repository.findById("key-7")).thenReturn(Optional.of(corrupted));

        TableManagementException exception = assertThrows(
                TableManagementException.class,
                () -> service.execute(
                        "key-7",
                        request,
                        Map.of(),
                        () -> ResponseEntity.status(HttpStatus.CREATED).body(new QrIssueResponse("ignored", "ignored"))));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.status());
        assertEquals(TableErrorCode.IDEMPOTENCY_REPLAY_UNAVAILABLE, exception.code());
        assertFalse(exception.getMessage().contains("not-a-valid-envelope"));
    }

    /** Mirrors TableIdempotencyService's private requestHash() so the fixture record matches. */
    private String requestHashOf(Object requestBody) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = objectMapper.writeValueAsBytes(requestBody);
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] key(byte fill) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, fill);
        return bytes;
    }
}
