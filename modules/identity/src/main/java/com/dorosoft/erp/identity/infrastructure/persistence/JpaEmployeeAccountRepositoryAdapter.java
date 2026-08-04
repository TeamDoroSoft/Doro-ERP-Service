package com.dorosoft.erp.identity.infrastructure.persistence;

import com.dorosoft.erp.identity.application.port.EmployeeAccountRepository;
import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import com.dorosoft.erp.identity.infrastructure.persistence.entity.EmployeeAccountJpaEntity;
import com.dorosoft.erp.identity.infrastructure.persistence.repository.EmployeeAccountSpringDataRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaEmployeeAccountRepositoryAdapter implements EmployeeAccountRepository {
    private final EmployeeAccountSpringDataRepository repository;

    public JpaEmployeeAccountRepositoryAdapter(EmployeeAccountSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeAccount> findById(UUID accountId) {
        return repository.findById(accountId.toString()).map(EmployeeAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<EmployeeAccount> findByIdForUpdate(UUID accountId) {
        return repository.findByIdForUpdate(accountId.toString()).map(EmployeeAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<EmployeeAccount> findByNormalizedLoginIdForUpdate(String normalizedLoginId) {
        return repository.findByLoginIdForUpdate(normalizedLoginId).map(EmployeeAccountJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeAccount> findAllOrderedById() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(EmployeeAccountJpaEntity::toDomain)
                .toList();
    }

    @Override
    public EmployeeAccount incrementVersion(UUID accountId, long expectedVersion) {
        if (repository.incrementVersion(accountId.toString(), expectedVersion) != 1) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    EmployeeAccountJpaEntity.class, accountId);
        }
        return repository.findById(accountId.toString())
                .orElseThrow(() -> new IllegalStateException("versioned account disappeared"))
                .toDomain();
    }

    @Override
    public EmployeeAccount save(EmployeeAccount account) {
        return repository.saveAndFlush(EmployeeAccountJpaEntity.fromDomain(account)).toDomain();
    }
}
