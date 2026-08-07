package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeAccountRepository {

    Optional<EmployeeAccount> findById(UUID id);

    Optional<EmployeeAccount> findByTenantIdAndLoginId(UUID tenantId, LoginId loginId);

    EmployeeAccount save(EmployeeAccount account);
}
