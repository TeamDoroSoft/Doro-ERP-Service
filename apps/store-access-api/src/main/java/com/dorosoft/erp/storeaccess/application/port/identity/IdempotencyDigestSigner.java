package com.dorosoft.erp.storeaccess.application.port.identity;

/**
 * Computes non-reversible digests for the idempotency key and canonical request (ADR-02-014). The raw
 * {@code Idempotency-Key} and request body are never stored; only these digests are.
 */
public interface IdempotencyDigestSigner {

    String digestKey(String rawIdempotencyKey);

    String digestRequest(String canonicalRequest);
}
