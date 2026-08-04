package com.dorosoft.erp.audit.infrastructure;

import com.dorosoft.erp.audit.application.api.AuditContractException;
import com.dorosoft.erp.audit.application.api.AuditErrorCode;
import com.dorosoft.erp.audit.application.port.AuditPayloadSigner;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/** Active/previous HMAC key ring: new records use active while retries may verify either key. */
public final class HmacSha256AuditPayloadSigner implements AuditPayloadSigner {
    private static final int MINIMUM_KEY_BYTES = 32;
    private final byte[] activeKey;
    private final byte[] previousKey;

    public HmacSha256AuditPayloadSigner(byte[] key) {
        this(key, null);
    }

    public HmacSha256AuditPayloadSigner(byte[] activeKey, byte[] previousKey) {
        if (activeKey == null || activeKey.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("Audit HMAC key must contain at least 32 bytes");
        }
        if (previousKey != null && previousKey.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("Previous audit HMAC key must contain at least 32 bytes");
        }
        if (previousKey != null && MessageDigest.isEqual(activeKey, previousKey)) {
            throw new IllegalArgumentException("Audit HMAC active and previous keys must differ");
        }
        this.activeKey = Arrays.copyOf(activeKey, activeKey.length);
        this.previousKey = previousKey == null ? null : Arrays.copyOf(previousKey, previousKey.length);
    }

    @Override
    public byte[] sign(byte[] canonicalPayload) {
        if (canonicalPayload == null) {
            throw new IllegalArgumentException("Canonical audit payload is required");
        }
        try {
            return hmac(activeKey, canonicalPayload);
        } catch (GeneralSecurityException exception) {
            throw new AuditContractException(AuditErrorCode.AUDIT_UNAVAILABLE, "Audit signer failed", exception);
        }
    }

    @Override
    public boolean matches(byte[] canonicalPayload, byte[] signature) {
        if (canonicalPayload == null || signature == null) {
            return false;
        }
        try {
            if (MessageDigest.isEqual(hmac(activeKey, canonicalPayload), signature)) {
                return true;
            }
            return previousKey != null
                    && MessageDigest.isEqual(hmac(previousKey, canonicalPayload), signature);
        } catch (GeneralSecurityException exception) {
            throw new AuditContractException(AuditErrorCode.AUDIT_UNAVAILABLE, "Audit signer failed", exception);
        }
    }

    private byte[] hmac(byte[] key, byte[] payload) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(payload);
    }
}
