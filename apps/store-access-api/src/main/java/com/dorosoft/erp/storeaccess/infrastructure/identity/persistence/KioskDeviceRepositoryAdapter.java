package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.application.port.identity.KioskDeviceRepository;
import com.dorosoft.erp.storeaccess.domain.identity.KioskDevice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class KioskDeviceRepositoryAdapter implements KioskDeviceRepository {

    private final KioskDeviceJpaRepository jpaRepository;

    KioskDeviceRepositoryAdapter(KioskDeviceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<KioskDevice> findById(UUID id) {
        return jpaRepository.findById(id).map(KioskDeviceEntity::toDomain);
    }

    @Override
    public Optional<KioskDevice> findByCredentialId(String credentialId) {
        return jpaRepository.findByCredentialId(credentialId).map(KioskDeviceEntity::toDomain);
    }

    @Override
    public KioskDevice save(KioskDevice device) {
        KioskDeviceEntity saved = jpaRepository.saveAndFlush(KioskDeviceEntity.fromDomain(device));
        return saved.toDomain();
    }
}
