package com.dorosoft.erp.storeaccess.domain.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Kiosk device credential (ADR-02-013). {@code secretDigest} stores only the HMAC-SHA-256 digest of the
 * device secret, never the plaintext secret.
 */
public record KioskDevice(
        UUID id,
        UUID tenantId,
        UUID storeId,
        String deviceCode,
        String credentialId,
        String secretDigest,
        int credentialVersion,
        KioskDeviceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    private static final int MAX_DEVICE_CODE_LENGTH = 100;
    private static final int MAX_CREDENTIAL_ID_LENGTH = 100;

    public KioskDevice {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(storeId, "storeId must not be null");
        requireNonBlank(deviceCode, "deviceCode", MAX_DEVICE_CODE_LENGTH);
        requireNonBlank(credentialId, "credentialId", MAX_CREDENTIAL_ID_LENGTH);
        if (secretDigest == null || secretDigest.isBlank()) {
            throw new IllegalArgumentException("secretDigest must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (credentialVersion < 1) {
            throw new IllegalArgumentException("credentialVersion must be at least 1");
        }
    }

    public static KioskDevice register(
            UUID id, UUID tenantId, UUID storeId, String deviceCode, String credentialId, String secretDigest, Instant now) {
        return new KioskDevice(
                id, tenantId, storeId, deviceCode, credentialId, secretDigest, 1, KioskDeviceStatus.ACTIVE, now, now);
    }

    /**
     * Issues a new credential with no grace period (ADR-02-013): the previous {@code credentialId} stops
     * resolving to this device immediately, since this replaces the stored value rather than appending one.
     */
    public KioskDevice rotate(String newCredentialId, String newSecretDigest, Instant now) {
        return new KioskDevice(
                id, tenantId, storeId, deviceCode, newCredentialId, newSecretDigest, credentialVersion + 1,
                status, createdAt, now);
    }

    /** Revokes the device (ADR-02-013): a status change, not a physical delete, so past orders are preserved. */
    public KioskDevice revoke(Instant now) {
        return new KioskDevice(
                id, tenantId, storeId, deviceCode, credentialId, secretDigest, credentialVersion + 1,
                KioskDeviceStatus.REVOKED, createdAt, now);
    }

    private static void requireNonBlank(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
    }
}
