package com.dorosoft.erp.storeaccess.infrastructure.identity.persistence;

import com.dorosoft.erp.storeaccess.application.port.identity.EmployeeAccountRepository;
import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class EmployeeAccountRepositoryAdapter implements EmployeeAccountRepository {

    private final EmployeeAccountJpaRepository jpaRepository;

    EmployeeAccountRepositoryAdapter(EmployeeAccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<EmployeeAccount> findById(UUID id) {
        return jpaRepository.findById(id).map(EmployeeAccountEntity::toDomain);
    }

    @Override
    public Optional<EmployeeAccount> findByTenantIdAndLoginId(UUID tenantId, LoginId loginId) {
        return jpaRepository.findByTenantIdAndLoginId(tenantId, loginId.value()).map(EmployeeAccountEntity::toDomain);
    }

    @Override
    public EmployeeAccount save(EmployeeAccount account) {
        EmployeeAccountEntity saved = jpaRepository.saveAndFlush(EmployeeAccountEntity.fromDomain(account));
        return saved.toDomain();
    }
}
