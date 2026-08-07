package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeSecurityHistoryRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeSecurityHistory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class EmployeeSecurityHistoryRepositoryAdapter implements EmployeeSecurityHistoryRepository {

    private final EmployeeSecurityHistoryJpaRepository jpaRepository;

    EmployeeSecurityHistoryRepositoryAdapter(EmployeeSecurityHistoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<EmployeeSecurityHistory> findById(UUID id) {
        return jpaRepository.findById(id).map(EmployeeSecurityHistoryEntity::toDomain);
    }

    @Override
    public EmployeeSecurityHistory save(EmployeeSecurityHistory history) {
        EmployeeSecurityHistoryEntity saved = jpaRepository.saveAndFlush(EmployeeSecurityHistoryEntity.fromDomain(history));
        return saved.toDomain();
    }
}
