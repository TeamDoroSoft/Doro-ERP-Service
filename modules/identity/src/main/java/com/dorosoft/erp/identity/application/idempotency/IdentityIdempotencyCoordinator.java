package com.dorosoft.erp.identity.application.idempotency;

import com.dorosoft.erp.identity.application.port.IdempotencyCryptoPort;
import com.dorosoft.erp.identity.application.port.IdentityIdempotencyRepository;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyStatus;
import com.dorosoft.erp.identity.domain.idempotency.IdentityIdempotencyRecord;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Row-claim protocol shared by the two approved idempotent Identity operations. */
public final class IdentityIdempotencyCoordinator {
    private final IdentityIdempotencyRepository repository;
    private final IdempotencyCryptoPort crypto;

    public IdentityIdempotencyCoordinator(
            IdentityIdempotencyRepository repository,
            IdempotencyCryptoPort crypto
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.crypto = Objects.requireNonNull(crypto);
    }

    public Claim claim(
            IdempotencyOperation operation,
            String rawKey,
            byte[] canonicalRequest,
            UUID requestedBy,
            Instant now
    ) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(rawKey);
        Objects.requireNonNull(canonicalRequest);
        Objects.requireNonNull(requestedBy);
        Objects.requireNonNull(now);
        if (rawKey.length() < 1 || rawKey.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 200 characters");
        }

        var candidates = crypto.keyDigestCandidates(operation, rawKey);
        for (var candidate : candidates) {
            Optional<IdentityIdempotencyRecord> found = repository.findForUpdate(operation, candidate.keyDigest());
            if (found.isEmpty()) {
                continue;
            }
            IdentityIdempotencyRecord record = found.get();
            if (record.isExpiredAt(now)) {
                repository.deleteExpiredLockedRecord(record.recordId(), now);
                continue;
            }
            return existing(operation, canonicalRequest, record);
        }

        String activeVersion = crypto.activeMasterKeyVersion();
        var active = candidates.stream()
                .filter(candidate -> candidate.masterKeyVersion().equals(activeVersion))
                .findFirst()
                .orElseThrow(() -> new IdempotencyUnavailableException("Active idempotency digest is unavailable"));
        byte[] requestHmac = crypto.requestHmac(operation, canonicalRequest, activeVersion);
        IdentityIdempotencyRecord processing = IdentityIdempotencyRecord.processing(
                UUID.randomUUID(), operation, active.keyDigest(), requestHmac,
                requestedBy, activeVersion, now);
        if (repository.tryInsertProcessing(processing)) {
            return new Fresh(processing);
        }

        IdentityIdempotencyRecord raced = repository.findForUpdate(operation, active.keyDigest())
                .orElseThrow(() -> new IdempotencyUnavailableException("Idempotency claim race could not be resolved"));
        return existing(operation, canonicalRequest, raced);
    }

    public IdentityIdempotencyRecord complete201(
            Fresh fresh, UUID targetAccountId, byte[] responseProjection, Instant completedAt) {
        var encrypted = crypto.encryptResponse(fresh.record().operation(), responseProjection);
        if (!encrypted.masterKeyVersion().equals(fresh.record().masterKeyVersion())) {
            throw new IdempotencyUnavailableException("Master key changed during an idempotent operation");
        }
        return repository.save(fresh.record().succeed(
                targetAccountId, 201, encrypted.ciphertext(), encrypted.nonce(), completedAt));
    }

    public IdentityIdempotencyRecord complete204(
            Fresh fresh, UUID targetAccountId, Instant completedAt) {
        return repository.save(fresh.record().succeed(targetAccountId, 204, null, null, completedAt));
    }

    private Claim existing(
            IdempotencyOperation operation,
            byte[] canonicalRequest,
            IdentityIdempotencyRecord record
    ) {
        byte[] requestHmac = crypto.requestHmac(operation, canonicalRequest, record.masterKeyVersion());
        if (!record.hasSameRequestHmac(requestHmac)) {
            return new Reused(record.targetAccountId());
        }
        if (record.status() != IdempotencyStatus.SUCCEEDED) {
            throw new IdempotencyUnavailableException("An idempotent operation is still incomplete");
        }
        byte[] response = record.httpStatus() == 201
                ? crypto.decryptResponse(
                        operation, record.masterKeyVersion(), record.responseNonce(), record.responseCiphertext())
                : null;
        return new Replay(record, response);
    }

    public sealed interface Claim permits Fresh, Replay, Reused {
    }

    public record Fresh(IdentityIdempotencyRecord record) implements Claim {
    }

    public record Replay(IdentityIdempotencyRecord record, byte[] responseProjection) implements Claim {
        public Replay {
            responseProjection = responseProjection == null ? null : responseProjection.clone();
        }

        @Override
        public byte[] responseProjection() {
            return responseProjection == null ? null : responseProjection.clone();
        }
    }

    public record Reused(UUID targetAccountId) implements Claim {
    }
}
