package com.dorosoft.erp.storeaccess.application.port.identity;

import com.dorosoft.erp.storeaccess.domain.identity.EmployeeAccount;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeAccountRepository {

    Optional<EmployeeAccount> findById(UUID id);

    Optional<EmployeeAccount> findByTenantIdAndLoginId(UUID tenantId, LoginId loginId);

    List<EmployeeAccount> findByTenantId(UUID tenantId);

    EmployeeAccount save(EmployeeAccount account);

    /**
     * Locks every active {@code OWNER} row for the tenant, in ID order, within the caller's transaction
     * (ADR-02-010). Every code path that changes an {@code OWNER}'s role or status away from active must go
     * through this same lock before deciding whether the change would remove the tenant's last active owner.
     */
    List<EmployeeAccount> findActiveOwnersForUpdate(UUID tenantId);
}
