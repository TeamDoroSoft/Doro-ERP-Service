package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.domain.identity.KioskDevice;
import java.util.Optional;
import java.util.UUID;

public interface KioskDeviceRepository {

    Optional<KioskDevice> findById(UUID id);

    Optional<KioskDevice> findByCredentialId(String credentialId);

    KioskDevice save(KioskDevice device);
}
