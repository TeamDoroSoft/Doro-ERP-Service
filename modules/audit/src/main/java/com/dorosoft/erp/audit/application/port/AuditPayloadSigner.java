package com.dorosoft.erp.audit.application.port;

import java.security.MessageDigest;

public interface AuditPayloadSigner {
    byte[] sign(byte[] canonicalPayload);

    /** Verifies stored payloads; rotating implementations may accept an active or previous key. */
    default boolean matches(byte[] canonicalPayload, byte[] signature) {
        return signature != null && MessageDigest.isEqual(sign(canonicalPayload), signature);
    }
}
