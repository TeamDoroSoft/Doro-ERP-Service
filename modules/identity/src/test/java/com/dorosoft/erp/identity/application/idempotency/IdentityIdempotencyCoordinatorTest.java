package com.dorosoft.erp.identity.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dorosoft.erp.identity.application.port.IdempotencyCryptoPort;
import com.dorosoft.erp.identity.application.port.IdentityIdempotencyRepository;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import com.dorosoft.erp.identity.domain.idempotency.IdentityIdempotencyRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityIdempotencyCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private final MemoryRepository repository = new MemoryRepository();
    private final IdentityIdempotencyCoordinator coordinator =
            new IdentityIdempotencyCoordinator(repository, new TestCrypto());

    @Test
    void successfulCreateIsEncryptedAndReplayedWithoutAnotherFreshClaim() {
        UUID actor = UUID.randomUUID();
        byte[] request = "same-request".getBytes(StandardCharsets.UTF_8);
        var first = coordinator.claim(
                IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, "key-1", request, actor, NOW);
        assertThat(first).isInstanceOf(IdentityIdempotencyCoordinator.Fresh.class);

        byte[] projection = "response-projection".getBytes(StandardCharsets.UTF_8);
        coordinator.complete201(
                (IdentityIdempotencyCoordinator.Fresh) first, UUID.randomUUID(), projection, NOW);

        var replay = coordinator.claim(
                IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, "key-1", request, actor, NOW.plusSeconds(1));
        assertThat(replay).isInstanceOf(IdentityIdempotencyCoordinator.Replay.class);
        assertThat(((IdentityIdempotencyCoordinator.Replay) replay).responseProjection())
                .containsExactly(projection);
    }

    @Test
    void sameKeyWithDifferentCanonicalRequestIsRejectedAsReuse() {
        UUID actor = UUID.randomUUID();
        var first = (IdentityIdempotencyCoordinator.Fresh) coordinator.claim(
                IdempotencyOperation.EMPLOYEE_PASSWORD_RESET, "key-2",
                new byte[]{1}, actor, NOW);
        coordinator.complete204(first, UUID.randomUUID(), NOW);

        assertThat(coordinator.claim(
                IdempotencyOperation.EMPLOYEE_PASSWORD_RESET, "key-2",
                new byte[]{2}, actor, NOW.plusSeconds(1)))
                .isInstanceOf(IdentityIdempotencyCoordinator.Reused.class);
    }

    @Test
    void missingMasterKeyNeverFallsBackToPlaceholderCrypto() {
        var unavailable = new UnavailableIdempotencyCryptoPort();
        assertThatThrownBy(() -> unavailable.keyDigestCandidates(
                IdempotencyOperation.EMPLOYEE_ACCOUNT_CREATE, "key"))
                .isInstanceOf(IdempotencyUnavailableException.class);
    }

    private static final class TestCrypto implements IdempotencyCryptoPort {
        @Override
        public List<KeyDigestCandidate> keyDigestCandidates(
                IdempotencyOperation operation, String rawIdempotencyKey) {
            return List.of(new KeyDigestCandidate("v1", digest(
                    (operation + ":" + rawIdempotencyKey).getBytes(StandardCharsets.UTF_8))));
        }

        @Override
        public byte[] requestHmac(
                IdempotencyOperation operation, byte[] canonicalRequest, String masterKeyVersion) {
            return digest(canonicalRequest);
        }

        @Override
        public EncryptedPayload encryptResponse(IdempotencyOperation operation, byte[] responseProjection) {
            byte[] ciphertext = responseProjection.clone();
            for (int index = 0; index < ciphertext.length; index++) {
                ciphertext[index] ^= 0x5a;
            }
            return new EncryptedPayload("v1", new byte[12], ciphertext);
        }

        @Override
        public byte[] decryptResponse(
                IdempotencyOperation operation, String masterKeyVersion,
                byte[] nonce, byte[] ciphertext) {
            byte[] plain = ciphertext.clone();
            for (int index = 0; index < plain.length; index++) {
                plain[index] ^= 0x5a;
            }
            return plain;
        }

        @Override
        public String activeMasterKeyVersion() {
            return "v1";
        }

        private static byte[] digest(byte[] value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static final class MemoryRepository implements IdentityIdempotencyRepository {
        private final Map<String, IdentityIdempotencyRecord> records = new HashMap<>();

        @Override
        public Optional<IdentityIdempotencyRecord> findForUpdate(
                IdempotencyOperation operation, byte[] keyDigest) {
            return Optional.ofNullable(records.get(key(operation, keyDigest)));
        }

        @Override
        public boolean tryInsertProcessing(IdentityIdempotencyRecord record) {
            return records.putIfAbsent(key(record.operation(), record.keyDigest()), record) == null;
        }

        @Override
        public IdentityIdempotencyRecord save(IdentityIdempotencyRecord record) {
            records.put(key(record.operation(), record.keyDigest()), record);
            return record;
        }

        @Override
        public void deleteExpiredLockedRecord(UUID recordId, Instant now) {
            records.values().removeIf(record -> record.recordId().equals(recordId) && record.isExpiredAt(now));
        }

        private static String key(IdempotencyOperation operation, byte[] digest) {
            return operation + ":" + Arrays.toString(digest);
        }
    }
}
