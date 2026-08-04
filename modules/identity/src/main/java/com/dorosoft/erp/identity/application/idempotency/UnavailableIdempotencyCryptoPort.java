package com.dorosoft.erp.identity.application.idempotency;

import com.dorosoft.erp.identity.application.port.IdempotencyCryptoPort;
import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import java.util.List;

/**
 * Development-safe fail-closed adapter used when the master key is not configured.
 * It deliberately never supplies placeholder key material.
 */
public final class UnavailableIdempotencyCryptoPort implements IdempotencyCryptoPort {
    private IdempotencyUnavailableException unavailable() {
        return new IdempotencyUnavailableException("Identity idempotency key material is unavailable");
    }

    @Override
    public List<KeyDigestCandidate> keyDigestCandidates(IdempotencyOperation operation, String rawIdempotencyKey) {
        throw unavailable();
    }

    @Override
    public byte[] requestHmac(IdempotencyOperation operation, byte[] canonicalRequest, String masterKeyVersion) {
        throw unavailable();
    }

    @Override
    public EncryptedPayload encryptResponse(IdempotencyOperation operation, byte[] responseProjection) {
        throw unavailable();
    }

    @Override
    public byte[] decryptResponse(
            IdempotencyOperation operation, String masterKeyVersion, byte[] nonce, byte[] ciphertext) {
        throw unavailable();
    }

    @Override
    public String activeMasterKeyVersion() {
        throw unavailable();
    }
}
