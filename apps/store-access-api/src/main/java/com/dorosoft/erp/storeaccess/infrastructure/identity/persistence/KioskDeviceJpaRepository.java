package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KioskDeviceJpaRepository extends JpaRepository<KioskDeviceEntity, UUID> {

    Optional<KioskDeviceEntity> findByCredentialId(String credentialId);
}
