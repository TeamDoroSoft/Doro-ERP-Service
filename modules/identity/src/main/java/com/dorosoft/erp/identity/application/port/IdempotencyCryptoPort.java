package com.dorosoft.erp.identity.application.port;

import com.dorosoft.erp.identity.domain.idempotency.IdempotencyOperation;
import java.util.List;

/** 원문 Key·요청·응답을 영속 계층 밖에서 HMAC·암호화하는 경계. */
public interface IdempotencyCryptoPort {
    List<KeyDigestCandidate> keyDigestCandidates(IdempotencyOperation operation, String rawIdempotencyKey);

    byte[] requestHmac(IdempotencyOperation operation, byte[] canonicalRequest, String masterKeyVersion);

    EncryptedPayload encryptResponse(IdempotencyOperation operation, byte[] responseProjection);

    byte[] decryptResponse(
            IdempotencyOperation operation,
            String masterKeyVersion,
            byte[] nonce,
            byte[] ciphertext
    );

    String activeMasterKeyVersion();

    record KeyDigestCandidate(String masterKeyVersion, byte[] keyDigest) {
        public KeyDigestCandidate {
            keyDigest = keyDigest.clone();
        }

        @Override
        public byte[] keyDigest() {
            return keyDigest.clone();
        }
    }

    record EncryptedPayload(String masterKeyVersion, byte[] nonce, byte[] ciphertext) {
        public EncryptedPayload {
            nonce = nonce.clone();
            ciphertext = ciphertext.clone();
        }

        @Override public byte[] nonce() { return nonce.clone(); }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
    }
}
