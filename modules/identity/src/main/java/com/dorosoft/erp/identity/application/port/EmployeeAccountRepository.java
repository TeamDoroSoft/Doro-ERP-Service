package com.dorosoft.erp.identity.application.port;

import com.dorosoft.erp.identity.domain.account.EmployeeAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeAccountRepository {
    Optional<EmployeeAccount> findById(UUID accountId);

    Optional<EmployeeAccount> findByIdForUpdate(UUID accountId);

    Optional<EmployeeAccount> findByNormalizedLoginIdForUpdate(String normalizedLoginId);

    List<EmployeeAccount> findAllOrderedById();

    EmployeeAccount incrementVersion(UUID accountId, long expectedVersion);

    EmployeeAccount save(EmployeeAccount account);
}
