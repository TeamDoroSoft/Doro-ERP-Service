package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.domain.identity.KioskDevice;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDeviceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kiosk_device")
class KioskDeviceEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "device_code", nullable = false, length = 100)
    private String deviceCode;

    @Column(name = "credential_id", nullable = false, length = 100)
    private String credentialId;

    @Column(name = "secret_digest", nullable = false, length = 255)
    private String secretDigest;

    @Column(name = "credential_version", nullable = false)
    private int credentialVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KioskDeviceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_authenticated_at")
    private Instant lastAuthenticatedAt;

    protected KioskDeviceEntity() {
    }

    private KioskDeviceEntity(
            UUID id, UUID tenantId, UUID storeId, String deviceCode, String credentialId, String secretDigest,
            int credentialVersion, KioskDeviceStatus status, Instant createdAt, Instant updatedAt,
            Instant lastAuthenticatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.deviceCode = deviceCode;
        this.credentialId = credentialId;
        this.secretDigest = secretDigest;
        this.credentialVersion = credentialVersion;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastAuthenticatedAt = lastAuthenticatedAt;
    }

    static KioskDeviceEntity fromDomain(KioskDevice device) {
        return new KioskDeviceEntity(
                device.id(),
                device.tenantId(),
                device.storeId(),
                device.deviceCode(),
                device.credentialId(),
                device.secretDigest(),
                device.credentialVersion(),
                device.status(),
                device.createdAt(),
                device.updatedAt(),
                device.lastAuthenticatedAt());
    }

    KioskDevice toDomain() {
        return new KioskDevice(
                id, tenantId, storeId, deviceCode, credentialId, secretDigest, credentialVersion, status,
                createdAt, updatedAt, lastAuthenticatedAt);
    }
}
